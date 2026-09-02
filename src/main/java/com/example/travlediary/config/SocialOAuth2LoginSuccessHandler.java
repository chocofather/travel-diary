package com.example.travlediary.config;

import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.SocialAccountService;
import com.example.travlediary.service.user.UserSanctionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SocialOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Duration SIGNUP_TTL = Duration.ofMinutes(10);
    private static final String NAVER_SUCCESS_RESULT_CODE = "00";

    private final SocialAccountService socialAccountService;
    private final UserMapper userMapper;
    private final UserSanctionService userSanctionService;
    private final CustomLoginSuccessHandler customLoginSuccessHandler;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauthAuthentication)) {
                reject(request, response);
                return;
            }

            SocialIdentity identity = extractIdentity(oauthAuthentication);
            if (identity == null) {
                reject(request, response);
                return;
            }

            SocialAccount socialAccount = socialAccountService
                    .findByProviderAndProviderUserId(
                            identity.provider(), identity.providerUserId());
            if (socialAccount == null) {
                beginSignup(request, response, identity);
                return;
            }

            loginConnectedAccount(request, response, socialAccount);
        } catch (RuntimeException exception) {
            reject(request, response);
        }
    }

    private SocialIdentity extractIdentity(OAuth2AuthenticationToken authentication) {
        SocialProvider provider = SocialProvider.fromRegistrationId(
                        authentication.getAuthorizedClientRegistrationId())
                .orElse(null);
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case GOOGLE, KAKAO -> extractOidcIdentity(provider, authentication.getPrincipal());
            case NAVER -> extractNaverIdentity(authentication.getPrincipal());
        };
    }

    private SocialIdentity extractOidcIdentity(SocialProvider provider, Object principal) {
        if (!(principal instanceof OidcUser oidcUser)) {
            return null;
        }
        String providerUserId = normalizeRequired(oidcUser.getSubject());
        if (providerUserId == null) {
            return null;
        }
        return new SocialIdentity(
                provider,
                providerUserId,
                normalizeOptional(oidcUser.getEmail()),
                oidcUser.getEmailVerified());
    }

    private SocialIdentity extractNaverIdentity(Object principal) {
        if (!(principal instanceof OAuth2User oauth2User)) {
            return null;
        }
        Map<String, Object> attributes = oauth2User.getAttributes();
        if (attributes == null
                || !NAVER_SUCCESS_RESULT_CODE.equals(attributes.get("resultcode"))) {
            return null;
        }
        Object response = attributes.get("response");
        if (!(response instanceof Map<?, ?> responseAttributes)) {
            return null;
        }
        String providerUserId = stringAttribute(responseAttributes, "id", true);
        if (providerUserId == null) {
            return null;
        }
        return new SocialIdentity(
                SocialProvider.NAVER,
                providerUserId,
                stringAttribute(responseAttributes, "email", false),
                null);
    }

    private String stringAttribute(Map<?, ?> attributes, String name, boolean required) {
        Object value = attributes.get(name);
        if (!(value instanceof String text)) {
            return null;
        }
        return required ? normalizeRequired(text) : normalizeOptional(text);
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void loginConnectedAccount(HttpServletRequest request,
                                       HttpServletResponse response,
                                       SocialAccount socialAccount) throws IOException {
        Long userId = socialAccount.getUserId();
        User user = userId == null ? null : userMapper.findById(userId);
        if (!canAuthenticate(user)) {
            reject(request, response);
            return;
        }

        if (user.getStatus() == UserStatus.RESTRICTED) {
            userSanctionService.releaseIfExpired(user.getId());
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken internalAuthentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        userDetails, null, userDetails.getAuthorities());

        request.getSession().removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(internalAuthentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        customLoginSuccessHandler.onAuthenticationSuccess(
                request, response, internalAuthentication);
    }

    private boolean canAuthenticate(User user) {
        if (user == null || user.getId() == null || user.getUserRole() == null
                || user.getStatus() == null) {
            return false;
        }
        return user.getStatus() == UserStatus.ACTIVE
                || user.getStatus() == UserStatus.RESTRICTED;
    }

    private void beginSignup(HttpServletRequest request,
                             HttpServletResponse response,
                             SocialIdentity identity) throws IOException {
        Instant createdAt = Instant.now();
        PendingSocialSignup pending = new PendingSocialSignup(
                UUID.randomUUID().toString(),
                identity.provider(),
                identity.providerUserId(),
                identity.providerEmail(),
                identity.providerEmailVerified(),
                createdAt,
                createdAt.plus(SIGNUP_TTL));

        request.getSession().setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE, pending);
        request.getSession().removeAttribute("userId");
        clearAuthentication(request, response);
        response.sendRedirect("/social-signup");
    }

    private void reject(HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        request.getSession().removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        request.getSession().removeAttribute("userId");
        clearAuthentication(request, response);
        response.sendRedirect(OAuth2LoginFailureHandler.FAILURE_REDIRECT);
    }

    private void clearAuthentication(HttpServletRequest request,
                                     HttpServletResponse response) {
        SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(emptyContext);
        securityContextRepository.saveContext(emptyContext, request, response);
    }

    private record SocialIdentity(
            SocialProvider provider,
            String providerUserId,
            String providerEmail,
            Boolean providerEmailVerified) {
    }
}
