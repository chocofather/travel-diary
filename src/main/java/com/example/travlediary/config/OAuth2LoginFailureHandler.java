package com.example.travlediary.config;

import com.example.travlediary.model.PendingSocialConnection;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialConnectionNotice;
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
        Object connectionValue = request.getSession(false) == null ? null
                : request.getSession(false).getAttribute(
                        PendingSocialConnection.SESSION_ATTRIBUTE);
        if (connectionValue instanceof PendingSocialConnection pending
                && pending.matchesOAuthState(request.getParameter("state"))) {
            request.getSession(false).removeAttribute(
                    PendingSocialConnection.SESSION_ATTRIBUTE);
            request.getSession(false).removeAttribute(
                    PendingSocialWithdrawal.SESSION_ATTRIBUTE);
            request.getSession(false).removeAttribute(
                    PendingSocialSignup.SESSION_ATTRIBUTE);
            Object currentUserId = request.getSession(false).getAttribute("userId");
            if (currentUserId instanceof Long userId
                    && userId.equals(pending.userId())
                    && authenticationRestorer != null
                    && authenticationRestorer.restore(request, response, userId)) {
                request.getSession(false).setAttribute(
                        SocialConnectionNotice.SESSION_ATTRIBUTE,
                        new SocialConnectionNotice(
                                SocialConnectionNotice.Type.ERROR, pending.provider()));
                response.sendRedirect("/mypage/account");
                return;
            }
            response.sendRedirect(FAILURE_REDIRECT);
            return;
        }
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
