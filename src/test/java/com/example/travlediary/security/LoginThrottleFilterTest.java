package com.example.travlediary.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginThrottleFilterTest {

    @Mock
    private LoginThrottle throttle;
    @Mock
    private FilterChain filterChain;

    private LoginThrottleFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoginThrottleFilter(throttle);
    }

    @Test
    void blockedLoginDoesNotReachAuthenticationAndUsesTheGenericFailureUrl() throws Exception {
        MockHttpServletRequest request = loginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Instant blockedUntil = Instant.parse("2026-09-08T00:00:10Z");
        when(throttle.status("member", "203.0.113.25"))
                .thenReturn(new LoginThrottleStatus(5, blockedUntil));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
        LoginFormState formState = (LoginFormState) request.getSession()
                .getAttribute(LoginFormState.SESSION_ATTRIBUTE);
        assertThat(formState.username()).isEqualTo("member");
        assertThat(formState.failureCount()).isEqualTo(5);
        assertThat(formState.blockedUntil()).isEqualTo(blockedUntil);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void loginPathParametersCannotBypassTheThrottle() throws Exception {
        MockHttpServletRequest request = loginRequest();
        request.setRequestURI("/login;attempt=1");
        request.setServletPath("/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(throttle.status("member", "203.0.113.25"))
                .thenReturn(new LoginThrottleStatus(
                        5, Instant.parse("2026-09-08T00:00:10Z")));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void allowedLoginContinuesToTheAuthenticationFilter() throws Exception {
        MockHttpServletRequest request = loginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(throttle.status("member", "203.0.113.25"))
                .thenReturn(new LoginThrottleStatus(0, null));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void passwordRecoveryRequestIsNeverThrottled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/users/find-password");
        request.setRemoteAddr("203.0.113.25");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(throttle, never()).status("member", "203.0.113.25");
        verify(filterChain).doFilter(request, response);
    }

    private MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServletPath("/login");
        request.addParameter("username", "member");
        request.setRemoteAddr("203.0.113.25");
        return request;
    }
}
