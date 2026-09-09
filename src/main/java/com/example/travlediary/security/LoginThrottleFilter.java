package com.example.travlediary.security;

import com.example.travlediary.config.InternalRedirectValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class LoginThrottleFilter extends OncePerRequestFilter {

    public static final String FAILURE_REDIRECT = "/login?error=true";
    private static final RequestMatcher LOGIN_REQUEST =
            AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/login");

    private final LoginThrottle loginThrottle;

    public LoginThrottleFilter(LoginThrottle loginThrottle) {
        this.loginThrottle = loginThrottle;
    }

    /**
     * 로그인 실패 후에도 원래 페이지 복귀 대상을 잃지 않도록 redirect 를 다시 붙인다.
     * 내부 상대경로만 통과시키므로 외부 host 로는 되돌아가지 않는다.
     */
    public static String failureRedirect(HttpServletRequest request) {
        String redirect = InternalRedirectValidator.normalize(request.getParameter("redirect"));
        if (redirect == null) {
            return FAILURE_REDIRECT;
        }
        return FAILURE_REDIRECT + "&redirect="
                + URLEncoder.encode(redirect, StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN_REQUEST.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        LoginThrottleStatus status = loginThrottle.status(
                request.getParameter("username"), request.getRemoteAddr());
        if (status.blocked()) {
            request.getSession().setAttribute(
                    LoginFormState.SESSION_ATTRIBUTE,
                    LoginFormState.from(request.getParameter("username"), status));
            response.sendRedirect(request.getContextPath() + failureRedirect(request));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
