package com.example.travlediary.security;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 탈퇴 유예 격리. 인증이 허용된다는 것은 안내 화면까지만 들어온다는 뜻이지
 * 일반 서비스를 쓸 수 있다는 뜻이 아니다.
 */
@ExtendWith(MockitoExtension.class)
class WithdrawalPendingAccountFilterTest {

    @Mock
    private UserMapper userMapper;

    private WithdrawalPendingAccountFilter filter;

    @BeforeEach
    void setUp() {
        filter = new WithdrawalPendingAccountFilter(userMapper);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyOrdinaryServiceUrlIsSentToTheWithdrawalNoticePage() throws Exception {
        authenticate(UserRole.USER);
        when(userMapper.findStatusById(5L)).thenReturn(UserStatus.WITHDRAWAL_PENDING);

        for (String path : List.of("/", "/mypage", "/mypage/account", "/destinations/3",
                "/board/list", "/comments/list", "/courses/7", "/diary", "/bookmarks",
                "/travel-plans/1", "/users/recover-account")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request(path), response, chain);

            assertThat(response.getRedirectedUrl()).as("path %s", path)
                    .isEqualTo("/account/withdrawal-pending");
            verify(chain, never()).doFilter(any(), any());
        }
    }

    @Test
    void jsonRequestsGetForbiddenInsteadOfARedirect() throws Exception {
        authenticate(UserRole.USER);
        when(userMapper.findStatusById(5L)).thenReturn(UserStatus.WITHDRAWAL_PENDING);
        MockHttpServletRequest request = request("/bookmarks/posts/3");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("WithdrawalPending");
        assertThat(response.getRedirectedUrl()).isNull();
    }

    /** 안내 화면, 복구 확인, 로그아웃, 언어 전환, 정적 자원은 막히면 안 된다. */
    @Test
    void theNoticeScreenRecoveryFlowLogoutAndStaticResourcesAreNeverBlocked() throws Exception {
        authenticate(UserRole.USER);

        for (String path : List.of("/account/withdrawal-pending",
                "/account/withdrawal-pending/recovery-link",
                "/users/recover-account/confirm",
                "/logout", "/login", "/locale",
                "/css/login.css", "/js/language-menu.js", "/images/logo7.png",
                "/fonts/pretendard.woff2", "/uploads/events/a.jpg",
                "/favicon.ico", "/error")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(path), response, new MockFilterChain());
            assertThat(response.getRedirectedUrl()).as("path %s", path).isNull();
        }
        // 허용 경로는 상태 조회조차 하지 않는다 (리다이렉트 루프 방지)
        verify(userMapper, never()).findStatusById(anyLong());
    }

    /** 세션이 아니라 users.status 를 요청마다 다시 본다. 복구가 끝나면 바로 풀린다. */
    @Test
    void statusIsReadFromTheDatabaseOnEveryRequest() throws Exception {
        authenticate(UserRole.USER);
        when(userMapper.findStatusById(5L))
                .thenReturn(UserStatus.WITHDRAWAL_PENDING)
                .thenReturn(UserStatus.ACTIVE);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request("/mypage"), blocked, mock(FilterChain.class));
        assertThat(blocked.getRedirectedUrl()).isEqualTo("/account/withdrawal-pending");

        MockHttpServletResponse allowed = new MockHttpServletResponse();
        filter.doFilter(request("/mypage"), allowed, new MockFilterChain());
        assertThat(allowed.getRedirectedUrl()).isNull();
    }

    @Test
    void otherStatusesAreLeftToTheirOwnPolicies() throws Exception {
        authenticate(UserRole.USER);
        for (UserStatus status : List.of(UserStatus.ACTIVE, UserStatus.RESTRICTED)) {
            when(userMapper.findStatusById(5L)).thenReturn(status);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request("/mypage"), response, new MockFilterChain());

            assertThat(response.getRedirectedUrl()).as("status %s", status).isNull();
        }
    }

    @Test
    void anonymousRequestsPassThrough() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/events"), response, new MockFilterChain());

        assertThat(response.getRedirectedUrl()).isNull();
        verify(userMapper, never()).findStatusById(anyLong());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return request;
    }

    private void authenticate(UserRole role) {
        User user = new User();
        user.setId(5L);
        user.setUsername("travler");
        user.setUserPassword("encoded");
        user.setUserRole(role);
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "encoded",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
