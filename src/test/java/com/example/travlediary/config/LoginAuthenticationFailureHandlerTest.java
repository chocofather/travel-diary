package com.example.travlediary.config;

import com.example.travlediary.security.LoginThrottle;
import com.example.travlediary.security.LoginFormState;
import com.example.travlediary.security.LoginThrottleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginAuthenticationFailureHandlerTest {

    @Mock
    private LoginThrottle throttle;

    private LoginAuthenticationFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LoginAuthenticationFailureHandler(throttle);
    }

    @Test
    void failedAuthenticationRecordsTheSubmittedAccountAndRemoteIp() throws Exception {
        MockHttpServletRequest request = request("member", "203.0.113.25");
        request.addParameter("password", "very-secret-password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(throttle.recordFailure("member", "203.0.113.25"))
                .thenReturn(new LoginThrottleStatus(3, null));

        handler.onAuthenticationFailure(
                request, response, new BadCredentialsException("wrong password"));

        verify(throttle).recordFailure("member", "203.0.113.25");
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
        LoginFormState formState = (LoginFormState) request.getSession()
                .getAttribute(LoginFormState.SESSION_ATTRIBUTE);
        assertThat(formState.username()).isEqualTo("member");
        assertThat(formState.failureCount()).isEqualTo(3);
        assertThat(formState.blockedUntil()).isNull();
        assertThat(response.getRedirectedUrl())
                .doesNotContain("member", "very-secret-password");
        assertThat(Collections.list(request.getSession().getAttributeNames()))
                .containsExactly(LoginFormState.SESSION_ATTRIBUTE);
    }

    @Test
    void differentAuthenticationFailuresUseTheSamePublicResponse() throws Exception {
        MockHttpServletRequest firstRequest = request("missing", "203.0.113.25");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockHttpServletRequest secondRequest = request("disabled", "203.0.113.26");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        when(throttle.recordFailure("missing", "203.0.113.25"))
                .thenReturn(new LoginThrottleStatus(1, null));
        when(throttle.recordFailure("disabled", "203.0.113.26"))
                .thenReturn(new LoginThrottleStatus(1, null));

        handler.onAuthenticationFailure(
                firstRequest, firstResponse, new BadCredentialsException("unknown account"));
        handler.onAuthenticationFailure(
                secondRequest, secondResponse, new DisabledException("disabled account"));

        assertThat(firstResponse.getRedirectedUrl()).isEqualTo("/login?error=true");
        assertThat(secondResponse.getRedirectedUrl()).isEqualTo("/login?error=true");
    }

    /** 1회 실패 후 재시도해도 원래 상세페이지로 돌아가야 하므로 redirect 를 잃지 않는다. */
    @Test
    void failedLoginKeepsTheOriginalDetailPageForTheNextAttempt() throws Exception {
        when(throttle.recordFailure("member", "203.0.113.25"))
                .thenReturn(new LoginThrottleStatus(1, null));

        for (String detailPath : new String[]{"/destinations/15", "/post/13", "/course/9"}) {
            MockHttpServletRequest request = request("member", "203.0.113.25");
            request.addParameter("redirect", detailPath);
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationFailure(
                    request, response, new BadCredentialsException("wrong password"));

            assertThat(response.getRedirectedUrl())
                    .isEqualTo("/login?error=true&redirect="
                            + URLEncoder.encode(detailPath, StandardCharsets.UTF_8));
        }
    }

    /** 외부 host 로는 되돌아가지 않는다(open redirect 방지). */
    @Test
    void unsafeRedirectIsDroppedOnFailure() throws Exception {
        when(throttle.recordFailure("member", "203.0.113.25"))
                .thenReturn(new LoginThrottleStatus(1, null));

        for (String unsafe : new String[]{
                "https://evil.example/path",
                "http://evil.example/path",
                "//evil.example/path",
                "/post/13%0d%0aLocation:https://evil.example",
                "not-an-internal-path"}) {
            MockHttpServletRequest request = request("member", "203.0.113.25");
            request.addParameter("redirect", unsafe);
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationFailure(
                    request, response, new BadCredentialsException("wrong password"));

            assertThat(response.getRedirectedUrl())
                    .as("unsafe redirect %s", unsafe)
                    .isEqualTo("/login?error=true");
        }
    }

    private MockHttpServletRequest request(String username, String ipAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.addParameter("username", username);
        request.setRemoteAddr(ipAddress);
        return request;
    }
}
