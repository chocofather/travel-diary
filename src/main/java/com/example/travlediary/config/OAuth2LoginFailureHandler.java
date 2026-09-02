package com.example.travlediary.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    public static final String FAILURE_REDIRECT = "/login?oauthError=true";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {
        response.sendRedirect(FAILURE_REDIRECT);
    }
}
