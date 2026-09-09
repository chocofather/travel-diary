package com.example.travlediary.config;

import com.example.travlediary.security.LoginFormState;
import com.example.travlediary.security.LoginThrottle;
import com.example.travlediary.security.LoginThrottleFilter;
import com.example.travlediary.security.LoginThrottleStatus;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

public class LoginAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final LoginThrottle loginThrottle;

    public LoginAuthenticationFailureHandler(LoginThrottle loginThrottle) {
        this.loginThrottle = loginThrottle;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {
        LoginThrottleStatus status = loginThrottle.recordFailure(
                request.getParameter("username"), request.getRemoteAddr());
        request.getSession().setAttribute(
                LoginFormState.SESSION_ATTRIBUTE,
                LoginFormState.from(request.getParameter("username"), status));
        // 실패해도 원래 복귀 대상을 유지해야 재시도 성공 시 상세페이지로 돌아온다.
        response.sendRedirect(
                request.getContextPath() + LoginThrottleFilter.failureRedirect(request));
    }
}
