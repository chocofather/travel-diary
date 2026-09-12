package com.example.travlediary.security;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.service.user.MissingEmailRegistrationService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MissingEmailAccountFilterTest {

    @Mock
    private MissingEmailRegistrationService missingEmailRegistrationService;

    private MissingEmailAccountFilter filter;

    @BeforeEach
    void setUp() {
        filter = new MissingEmailAccountFilter(missingEmailRegistrationService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** 대상 회원은 URL 을 직접 쳐도 이메일 등록 화면으로 모인다. */
    @Test
    void aTargetMemberBrowsingAnyPageIsSentToTheEmailRegistrationScreen() throws Exception {
        authenticate(7L);
        when(missingEmailRegistrationService.requiresEmailRegistration(7L)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mypage");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/account/email-required");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void anApiCallGetsAForbiddenJsonBodyInsteadOfARedirect() throws Exception {
        authenticate(7L);
        when(missingEmailRegistrationService.requiresEmailRegistration(7L)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("EmailVerificationRequired");
        assertThat(response.getRedirectedUrl()).isNull();
    }

    /** 이메일이 이미 있는 회원과 비로그인 요청은 그대로 통과한다. */
    @Test
    void membersWithAnEmailAndAnonymousRequestsPassThrough() throws Exception {
        authenticate(7L);
        when(missingEmailRegistrationService.requiresEmailRegistration(7L)).thenReturn(false);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/mypage"),
                new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();

        SecurityContextHolder.clearContext();
        MockFilterChain anonymous = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/mypage"),
                new MockHttpServletResponse(), anonymous);
        assertThat(anonymous.getRequest()).isNotNull();
        verify(missingEmailRegistrationService, never()).requiresEmailRegistration(null);
    }

    /** 등록과 인증을 끝내는 데 필요한 경로는 검사하지 않는다. 리다이렉트 순환을 막는다. */
    @ParameterizedTest
    @ValueSource(strings = {
            "/account/email-required",
            "/account/email-required/email-status",
            "/users/verify",
            "/users/register/verify-waiting",
            "/users/verification/status",
            "/users/verification/resend",
            "/logout",
            "/login",
            "/locale",
            "/css/style.css",
            "/js/account-email-required.js",
            "/images/logo7.png",
            "/error"
    })
    void theScreensNeededToFinishRegistrationAreNeverBlocked(String path) throws Exception {
        authenticate(7L);
        when(missingEmailRegistrationService.requiresEmailRegistration(anyLong()))
                .thenReturn(true);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", path),
                new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).as("%s must stay reachable", path).isNotNull();
    }

    private void authenticate(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("member");
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
