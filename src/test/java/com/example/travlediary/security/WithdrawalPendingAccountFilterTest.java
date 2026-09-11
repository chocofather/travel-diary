package com.example.travlediary.security;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.service.user.WithdrawalGraceService;
import com.example.travlediary.service.user.WithdrawalGraceService.Outcome;
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
import org.springframework.mock.web.MockHttpSession;
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
 *
 * <p>유예 여부는 status 만이 아니라 purge_scheduled_at 으로 판정한다
 * ({@link WithdrawalGraceService}). 유예가 끝난 세션은 안내 화면에도 머물 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class WithdrawalPendingAccountFilterTest {

    @Mock
    private WithdrawalGraceService withdrawalGraceService;

    private WithdrawalPendingAccountFilter filter;

    @BeforeEach
    void setUp() {
        filter = new WithdrawalPendingAccountFilter(withdrawalGraceService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyOrdinaryServiceUrlIsSentToTheWithdrawalNoticePage() throws Exception {
        authenticate(UserRole.USER);
        when(withdrawalGraceService.resolveAccess(5L, null)).thenReturn(Outcome.IN_GRACE);

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
        when(withdrawalGraceService.resolveAccess(5L, null)).thenReturn(Outcome.IN_GRACE);
        MockHttpServletRequest request = request("/bookmarks/posts/3");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("WithdrawalPending");
        assertThat(response.getRedirectedUrl()).isNull();
    }

    /**
     * 안내 화면을 열어 둔 채 유예가 끝나 버린 세션. 여기 계속 갇히면 복구도 신규가입도 못 한다.
     * 기존 계정은 판정 시점에 최종 파기되므로 세션을 끊고 새 가입으로 보낸다.
     */
    @Test
    void anExpiredGracePeriodEndsTheSessionAndSendsTheMemberToSignup() throws Exception {
        authenticate(UserRole.USER);
        when(withdrawalGraceService.resolveAccess(5L, null)).thenReturn(Outcome.GRACE_ENDED);
        MockHttpServletRequest request = request("/mypage");
        MockHttpSession session = (MockHttpSession) request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/users/register?withdrawalExpired=true");
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void anExpiredGracePeriodAnswersJsonRequestsWithoutARedirect() throws Exception {
        authenticate(UserRole.USER);
        when(withdrawalGraceService.resolveAccess(5L, null)).thenReturn(Outcome.GRACE_ENDED);
        MockHttpServletRequest request = request("/bookmarks/posts/3");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("WithdrawalExpired");
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
        verify(withdrawalGraceService, never()).resolveAccess(anyLong(), any());
    }

    /** 세션이 아니라 서버 상태를 요청마다 다시 본다. 복구가 끝나면 바로 풀린다. */
    @Test
    void statusIsReadFromTheDatabaseOnEveryRequest() throws Exception {
        authenticate(UserRole.USER);
        when(withdrawalGraceService.resolveAccess(5L, null))
                .thenReturn(Outcome.IN_GRACE)
                .thenReturn(Outcome.NOT_PENDING);

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
        when(withdrawalGraceService.resolveAccess(5L, null)).thenReturn(Outcome.NOT_PENDING);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/mypage"), response, new MockFilterChain());

        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    void anonymousRequestsPassThrough() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/events"), response, new MockFilterChain());

        assertThat(response.getRedirectedUrl()).isNull();
        verify(withdrawalGraceService, never()).resolveAccess(anyLong(), any());
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
