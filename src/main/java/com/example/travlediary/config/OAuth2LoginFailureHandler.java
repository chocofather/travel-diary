package com.example.travlediary.config;

import com.example.travlediary.model.PendingSocialWithdrawal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    public static final String FAILURE_REDIRECT = "/login?oauthError=true";
    private final TravelDiaryAuthenticationRestorer authenticationRestorer;

    public OAuth2LoginFailureHandler() {
        this(null);
    }

    public OAuth2LoginFailureHandler(
            TravelDiaryAuthenticationRestorer authenticationRestorer) {
        this.authenticationRestorer = authenticationRestorer;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {
        Object value = request.getSession(false) == null ? null
                : request.getSession(false).getAttribute(
                        PendingSocialWithdrawal.SESSION_ATTRIBUTE);
        if (value instanceof PendingSocialWithdrawal pending) {
            request.getSession(false).removeAttribute(
                    PendingSocialWithdrawal.SESSION_ATTRIBUTE);
            Object currentUserId = request.getSession(false).getAttribute("userId");
            if (currentUserId instanceof Long userId
                    && userId.equals(pending.userId())
                    && authenticationRestorer != null
                    && authenticationRestorer.restore(request, response, userId)) {
                response.sendRedirect(
                        "/mypage/account?socialWithdrawalError=true");
                return;
            }
        }
        response.sendRedirect(FAILURE_REDIRECT);
    }
}
