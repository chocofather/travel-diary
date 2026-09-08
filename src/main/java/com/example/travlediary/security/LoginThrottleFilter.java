package com.example.travlediary.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class LoginThrottleFilter extends OncePerRequestFilter {

    public static final String FAILURE_REDIRECT = "/login?error=true";
    private static final RequestMatcher LOGIN_REQUEST =
            AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/login");

    private final LoginThrottle loginThrottle;

    public LoginThrottleFilter(LoginThrottle loginThrottle) {
        this.loginThrottle = loginThrottle;
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
            response.sendRedirect(request.getContextPath() + FAILURE_REDIRECT);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
