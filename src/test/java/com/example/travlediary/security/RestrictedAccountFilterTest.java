package com.example.travlediary.security;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.user.UserSanctionService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestrictedAccountFilterTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserSanctionService userSanctionService;

    private RestrictedAccountFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RestrictedAccountFilter(userMapper, userSanctionService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void restrictedMemberIsSentToTheRestrictedPage() throws Exception {
        authenticate(UserRole.USER);
        when(userMapper.findStatusById(5L)).thenReturn(UserStatus.RESTRICTED);
        when(userSanctionService.releaseIfExpired(5L)).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("/mypage"), response, chain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/account/restricted");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void jsonRequestsGetForbiddenInsteadOfARedirect() throws Exception {
        authenticate(UserRole.USER);
        when(userMapper.findStatusById(5L)).thenReturn(UserStatus.RESTRICTED);
        when(userSanctionService.releaseIfExpired(5L)).thenReturn(false);
        MockHttpServletRequest request = request("/bookmarks/posts/3");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Restricted");
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    void statusIsReadFromTheDatabaseOnEveryRequestSoNewRestrictionsApplyImmediately()
            throws Exception {
        authenticate(UserRole.USER);
        // 로그인 시점에는 정상, 이후 관리자가 정지시킨 상황
        when(userMapper.findStatusById(5L))
                .thenReturn(UserStatus.ACTIVE)
                .thenReturn(UserStatus.RESTRICTED);
        when(userSanctionService.releaseIfExpired(5L)).thenReturn(false);

        MockFilterChain firstChain = new MockFilterChain();
        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request("/mypage"), first, firstChain);
        assertThat(first.getRedirectedUrl()).isNull();

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("/mypage"), second, mock(FilterChain.class));
        assertThat(second.getRedirectedUrl()).isEqualTo("/account/restricted");
    }

    @Test
    void expiredTemporarySanctionIsReleasedAndTheRequestContinues() throws Exception {
        authenticate(UserRole.USER);
        when(userMapper.findStatusById(5L)).thenReturn(UserStatus.RESTRICTED);
        when(userSanctionService.releaseIfExpired(5L)).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("/mypage"), response, chain);

        assertThat(response.getRedirectedUrl()).isNull();
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminRequestsAreNeverChecked() throws Exception {
        authenticate(UserRole.ADMIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/admin/users"), response, new MockFilterChain());

        assertThat(response.getRedirectedUrl()).isNull();
        verify(userMapper, never()).findStatusById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void anonymousRequestsPassThrough() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/events"), response, new MockFilterChain());

        assertThat(response.getRedirectedUrl()).isNull();
        verify(userMapper, never()).findStatusById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void activeMembersAreNotRedirected() throws Exception {
        authenticate(UserRole.USER);
        when(userMapper.findStatusById(5L)).thenReturn(UserStatus.ACTIVE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/mypage"), response, new MockFilterChain());

        assertThat(response.getRedirectedUrl()).isNull();
        verify(userSanctionService, never()).releaseIfExpired(
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void restrictedPageLogoutAndStaticResourcesAreNeverBlocked() throws Exception {
        authenticate(UserRole.USER);

        for (String path : List.of("/account/restricted", "/logout", "/login",
                "/appeals/new", "/css/style.css", "/js/main.js", "/images/logo.png",
                "/fonts/pretendard.woff2", "/uploads/events/a.jpg", "/favicon.ico", "/error")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(path), response, new MockFilterChain());
            assertThat(response.getRedirectedUrl()).as("path %s", path).isNull();
        }
        // 허용 경로는 상태 조회조차 하지 않는다 (리다이렉트 루프 방지)
        verify(userMapper, never()).findStatusById(org.mockito.ArgumentMatchers.anyLong());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return request;
    }

    private void authenticate(UserRole role) {
        User user = new User();
        user.setId(5L);
        user.setUsername(role == UserRole.ADMIN ? "master" : "travler");
        user.setUserPassword("encoded");
        user.setUserRole(role);
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "encoded",
                        List.of(new SimpleGrantedAuthority(
                                role == UserRole.ADMIN ? "ROLE_ADMIN" : "ROLE_USER"))));
    }
}
