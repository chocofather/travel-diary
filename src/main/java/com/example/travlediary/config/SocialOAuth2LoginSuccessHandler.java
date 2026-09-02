package com.example.travlediary.config;

import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.SocialAccountService;
import com.example.travlediary.service.user.SocialWithdrawalService;
import com.example.travlediary.service.user.UserSanctionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class SocialOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Duration SIGNUP_TTL = Duration.ofMinutes(10);
    private static final String NAVER_SUCCESS_RESULT_CODE = "00";

    private final SocialAccountService socialAccountService;
    private final UserMapper userMapper;
    private final UserSanctionService userSanctionService;
    private final CustomLoginSuccessHandler customLoginSuccessHandler;
    private final SocialWithdrawalService socialWithdrawalService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final TravelDiaryAuthenticationRestorer authenticationRestorer;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    @Autowired
    public SocialOAuth2LoginSuccessHandler(
            SocialAccountService socialAccountService,
            UserMapper userMapper,
            UserSanctionService userSanctionService,
            CustomLoginSuccessHandler customLoginSuccessHandler,
            SocialWithdrawalService socialWithdrawalService,
            OAuth2AuthorizedClientService authorizedClientService,
            TravelDiaryAuthenticationRestorer authenticationRestorer) {
        this.socialAccountService = socialAccountService;
        this.userMapper = userMapper;
        this.userSanctionService = userSanctionService;
        this.customLoginSuccessHandler = customLoginSuccessHandler;
        this.socialWithdrawalService = socialWithdrawalService;
        this.authorizedClientService = authorizedClientService;
        this.authenticationRestorer = authenticationRestorer;
    }

    SocialOAuth2LoginSuccessHandler(
            SocialAccountService socialAccountService,
            UserMapper userMapper,
            UserSanctionService userSanctionService,
            CustomLoginSuccessHandler customLoginSuccessHandler) {
        this(socialAccountService, userMapper, userSanctionService,
                customLoginSuccessHandler, null, null, null);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        PendingSocialWithdrawal withdrawal = consumePendingWithdrawal(request);
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauthAuthentication)) {
                if (withdrawal == null) {
                    reject(request, response);
                } else {
                    failWithdrawal(request, response, withdrawal);
                }
                return;
            }

            SocialIdentity identity = extractIdentity(oauthAuthentication);
            if (identity == null) {
                removeAuthorizedClient(oauthAuthentication);
                if (withdrawal == null) {
                    reject(request, response);
                } else {
                    failWithdrawal(request, response, withdrawal);
                }
                return;
            }

            if (withdrawal != null) {
                completeWithdrawal(
                        request, response, oauthAuthentication, identity, withdrawal);
                return;
            }

            // 단순 로그인에는 provider token이 더 필요하지 않다.
            removeAuthorizedClient(oauthAuthentication);

            SocialAccount socialAccount = socialAccountService
                    .findByProviderAndProviderUserId(
                            identity.provider(), identity.providerUserId());
            if (socialAccount == null) {
                beginSignup(request, response, identity);
                return;
            }

            loginConnectedAccount(request, response, socialAccount);
        } catch (RuntimeException exception) {
            if (withdrawal == null) {
                reject(request, response);
            } else {
                failWithdrawal(request, response, withdrawal);
            }
        }
    }

    private void completeWithdrawal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    OAuth2AuthenticationToken authentication,
                                    SocialIdentity identity,
                                    PendingSocialWithdrawal pending) throws IOException {
        HttpSession session = request.getSession(false);
        Long currentUserId = session != null && session.getAttribute("userId") instanceof Long id
                ? id : null;
        if (socialWithdrawalService == null || authorizedClientService == null) {
            throw new IllegalStateException("Social withdrawal is unavailable");
        }
        OAuth2AuthorizedClient authorizedClient = authorizedClientService
                .loadAuthorizedClient(
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getName());
        String accessToken = authorizedClient == null
                || authorizedClient.getAccessToken() == null
                ? null : authorizedClient.getAccessToken().getTokenValue();
        removeAuthorizedClient(authentication);
        socialWithdrawalService.complete(
                pending,
                currentUserId,
                identity.provider(),
                identity.providerUserId(),
                accessToken);

        new CookieClearingLogoutHandler("JSESSIONID")
                .logout(request, response, authentication);
        new SecurityContextLogoutHandler()
                .logout(request, response, authentication);
        response.sendRedirect("/?withdrawn=true");
    }

    private PendingSocialWithdrawal consumePendingWithdrawal(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        synchronized (session) {
            Object value = session.getAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
            if (value instanceof PendingSocialWithdrawal pending) {
                session.removeAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
                return pending;
            }
            return null;
        }
    }

    private void failWithdrawal(HttpServletRequest request,
                                HttpServletResponse response,
                                PendingSocialWithdrawal pending) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
        }
        Object currentUserId = session == null ? null : session.getAttribute("userId");
        if (currentUserId instanceof Long userId
                && userId.equals(pending.userId())
                && authenticationRestorer != null
                && authenticationRestorer.restore(request, response, userId)) {
            response.sendRedirect("/mypage/account?socialWithdrawalError=true");
            return;
        }
        reject(request, response);
    }

    private void removeAuthorizedClient(OAuth2AuthenticationToken authentication) {
        if (authorizedClientService == null) {
            return;
        }
        authorizedClientService.removeAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName());
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
