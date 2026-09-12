package com.example.travlediary.config;

import com.example.travlediary.model.PendingSocialConnection;
import com.example.travlediary.model.PendingSocialLink;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialConnectionNotice;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.controller.user.EmailVerificationController;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.SocialAccountService;
import com.example.travlediary.service.user.SocialConnectionResult;
import com.example.travlediary.service.user.SocialEmailAccountResolver;
import com.example.travlediary.service.user.SocialWithdrawalService;
import com.example.travlediary.service.user.UserSanctionService;
import com.example.travlediary.service.user.WithdrawalGraceService;
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
    private static final Duration LINK_TTL = Duration.ofMinutes(10);
    private static final String NAVER_SUCCESS_RESULT_CODE = "00";

    /** provider 가 인증한 이메일을 받지 못했다. 임의로 가입시키지 않는다. */
    static final String UNVERIFIED_EMAIL_REDIRECT = "/login?socialEmailUnverified=true";
    /** 같은 이메일의 계정이 아직 이메일 인증 대기 중이다. */
    static final String VERIFICATION_PENDING_REDIRECT = "/login?socialEmailPending=true";
    /** 같은 이메일의 계정이 탈퇴 유예 중이다. 30일 동안 그 계정이 이메일을 점유한다. */
    static final String WITHDRAWAL_PENDING_REDIRECT = "/login?socialEmailWithdrawing=true";
    /** 같은 이메일의 계정이 휴면·최종탈퇴 상태다. */
    static final String BLOCKED_EMAIL_REDIRECT = "/login?socialEmailBlocked=true";
    /** 이메일 인증을 마치지 않은 소셜 가입 계정. 일반 회원가입과 같은 대기 화면을 쓴다. */
    static final String VERIFY_WAITING_REDIRECT = "/users/register/verify-waiting";

    private final SocialAccountService socialAccountService;
    private final UserMapper userMapper;
    private final UserSanctionService userSanctionService;
    private final CustomLoginSuccessHandler customLoginSuccessHandler;
    private final WithdrawalGraceService withdrawalGraceService;
    private final SocialWithdrawalService socialWithdrawalService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final TravelDiaryAuthenticationRestorer authenticationRestorer;
    private final SocialEmailAccountResolver socialEmailAccountResolver;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    @Autowired
    public SocialOAuth2LoginSuccessHandler(
            SocialAccountService socialAccountService,
            UserMapper userMapper,
            UserSanctionService userSanctionService,
            CustomLoginSuccessHandler customLoginSuccessHandler,
            WithdrawalGraceService withdrawalGraceService,
            SocialWithdrawalService socialWithdrawalService,
            OAuth2AuthorizedClientService authorizedClientService,
            TravelDiaryAuthenticationRestorer authenticationRestorer,
            SocialEmailAccountResolver socialEmailAccountResolver) {
        this.socialAccountService = socialAccountService;
        this.userMapper = userMapper;
        this.userSanctionService = userSanctionService;
        this.customLoginSuccessHandler = customLoginSuccessHandler;
        this.withdrawalGraceService = withdrawalGraceService;
        this.socialWithdrawalService = socialWithdrawalService;
        this.authorizedClientService = authorizedClientService;
        this.authenticationRestorer = authenticationRestorer;
        this.socialEmailAccountResolver = socialEmailAccountResolver;
    }

    SocialOAuth2LoginSuccessHandler(
            SocialAccountService socialAccountService,
            UserMapper userMapper,
            UserSanctionService userSanctionService,
            CustomLoginSuccessHandler customLoginSuccessHandler,
            WithdrawalGraceService withdrawalGraceService,
            SocialEmailAccountResolver socialEmailAccountResolver) {
        this(socialAccountService, userMapper, userSanctionService,
                customLoginSuccessHandler, withdrawalGraceService, null, null, null,
                socialEmailAccountResolver);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        PendingSocialConnection connection = consumePendingConnection(request);
        PendingSocialWithdrawal withdrawal = consumePendingWithdrawal(request);
        try {
            if (connection != null && withdrawal != null) {
                failConnection(request, response, connection);
                return;
            }
            if (!(authentication instanceof OAuth2AuthenticationToken oauthAuthentication)) {
                if (connection != null) {
                    failConnection(request, response, connection);
                } else if (withdrawal == null) {
                    reject(request, response);
                } else {
                    failWithdrawal(request, response, withdrawal);
                }
                return;
            }

            SocialIdentity identity = extractIdentity(oauthAuthentication);
            if (identity == null) {
                removeAuthorizedClient(oauthAuthentication);
                if (connection != null) {
                    failConnection(request, response, connection);
                } else if (withdrawal == null) {
                    reject(request, response);
                } else {
                    failWithdrawal(request, response, withdrawal);
                }
                return;
            }

            if (connection != null) {
                completeConnection(
                        request, response, oauthAuthentication, identity, connection);
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
                beginNewIdentityFlow(request, response, identity);
                return;
            }

            loginConnectedAccount(request, response, identity, socialAccount);
        } catch (RuntimeException exception) {
            if (connection != null) {
                failConnection(request, response, connection);
            } else if (withdrawal == null) {
                reject(request, response);
            } else {
                failWithdrawal(request, response, withdrawal);
            }
        }
    }

    private void completeConnection(HttpServletRequest request,
                                    HttpServletResponse response,
                                    OAuth2AuthenticationToken authentication,
                                    SocialIdentity identity,
                                    PendingSocialConnection pending) throws IOException {
        HttpSession session = request.getSession(false);
        Long currentUserId = session != null && session.getAttribute("userId") instanceof Long id
                ? id : null;
        removeAuthorizedClient(authentication);
        if (!pending.isValidAt(Instant.now())
                || !pending.userId().equals(currentUserId)
                || pending.provider() != identity.provider()) {
            failConnection(request, response, pending);
            return;
        }
        if (authenticationRestorer == null
                || !authenticationRestorer.restore(request, response, pending.userId())) {
            reject(request, response);
            return;
        }

        SocialConnectionResult result = socialAccountService.connectToUser(
                pending.userId(),
                identity.provider(),
                identity.providerUserId(),
                identity.providerEmail(),
                identity.providerEmailVerified());
        SocialConnectionNotice.Type noticeType = switch (result) {
            case CONNECTED -> SocialConnectionNotice.Type.CONNECTED;
            case ALREADY_CONNECTED -> SocialConnectionNotice.Type.ALREADY_CONNECTED;
            case OWNED_BY_ANOTHER_USER -> SocialConnectionNotice.Type.ERROR;
        };
        request.getSession().removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        request.getSession().setAttribute(
                SocialConnectionNotice.SESSION_ATTRIBUTE,
                new SocialConnectionNotice(noticeType, pending.provider()));
        response.sendRedirect("/mypage/account");
    }

    private PendingSocialConnection consumePendingConnection(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        synchronized (session) {
            Object value = session.getAttribute(PendingSocialConnection.SESSION_ATTRIBUTE);
            if (value instanceof PendingSocialConnection pending
                    && pending.matchesOAuthState(request.getParameter("state"))) {
                session.removeAttribute(PendingSocialConnection.SESSION_ATTRIBUTE);
                return pending;
            }
            return null;
        }
    }

    private void failConnection(HttpServletRequest request,
                                HttpServletResponse response,
                                PendingSocialConnection pending) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(PendingSocialConnection.SESSION_ATTRIBUTE);
            session.removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        }
        Object currentUserId = session == null ? null : session.getAttribute("userId");
        if (currentUserId instanceof Long userId
                && userId.equals(pending.userId())
                && authenticationRestorer != null
                && authenticationRestorer.restore(request, response, userId)) {
            session.setAttribute(
                    SocialConnectionNotice.SESSION_ATTRIBUTE,
                    new SocialConnectionNotice(
                            SocialConnectionNotice.Type.ERROR, pending.provider()));
            response.sendRedirect("/mypage/account");
            return;
        }
        reject(request, response);
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
                                       SocialIdentity identity,
                                       SocialAccount socialAccount) throws IOException {
        Long userId = socialAccount.getUserId();
        User user = userId == null ? null : userMapper.findById(userId);

        // 이메일 인증을 아직 마치지 않은 소셜 가입 계정. 새 가입 화면을 다시 띄우거나
        // 새 users 를 만들지 않고, 일반 회원가입과 같은 인증 대기 화면으로 보낸다.
        if (isAwaitingEmailVerification(user)) {
            sendToVerificationWaiting(request, response, user);
            return;
        }
        if (!canAuthenticate(user)) {
            reject(request, response);
            return;
        }

        // 유예가 이미 끝난 계정이면 안내 화면으로 보내지 않는다. provider 인증은 방금 끝났으므로
        // 기존 계정을 최종 파기해 연결을 풀고, 같은 provider 식별정보로 신규 가입을 이어간다.
        if (user.getStatus() == UserStatus.WITHDRAWAL_PENDING
                && withdrawalGraceService.resolveAccess(user.getId(), identity.provider())
                        == WithdrawalGraceService.Outcome.GRACE_ENDED) {
            beginNewIdentityFlow(request, response, identity);
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
        request.getSession().removeAttribute(PendingSocialLink.SESSION_ATTRIBUTE);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(internalAuthentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        customLoginSuccessHandler.onAuthenticationSuccess(
                request, response, internalAuthentication);
    }

    /**
     * social_accounts 로 찾아온 회원이 아직 이메일 인증 대기 중인지 본다.
     * 이메일 없는 옛 소셜 계정과 섞이지 않도록 user_email 이 있는 경우만 인정한다.
     */
    private boolean isAwaitingEmailVerification(User user) {
        return user != null
                && user.getId() != null
                && user.getStatus() == UserStatus.INACTIVE
                && user.getDeletedAt() == null
                && user.getUserEmail() != null
                && !user.getUserEmail().isBlank();
    }

    private void sendToVerificationWaiting(HttpServletRequest request,
                                           HttpServletResponse response,
                                           User user) throws IOException {
        HttpSession session = request.getSession();
        session.removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        session.removeAttribute(PendingSocialLink.SESSION_ATTRIBUTE);
        session.removeAttribute("userId");
        // 인증 대기 화면과 재발송은 일반 회원가입과 같은 세션 값을 그대로 쓴다.
        session.setAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE,
                user.getUserEmail());
        clearAuthentication(request, response);
        response.sendRedirect(VERIFY_WAITING_REDIRECT);
    }

    private boolean canAuthenticate(User user) {
        if (user == null || user.getId() == null || user.getUserRole() == null
                || user.getStatus() == null) {
            return false;
        }
        // 탈퇴 유예 회원도 기존 소셜 연결이 그대로 남아 있으므로 인증까지는 허용한다.
        // 이동 화면과 접근 통제는 CustomLoginSuccessHandler 와 격리 필터가 맡는다.
        return user.getStatus() == UserStatus.ACTIVE
                || user.getStatus() == UserStatus.RESTRICTED
                || user.getStatus() == UserStatus.WITHDRAWAL_PENDING;
    }

    /**
     * 아직 우리 DB 에 없는 provider 식별자를 어떻게 처리할지 정한다.
     *
     * <p>Google 은 인증된 이메일이 곧 Travel Diary 의 공식 이메일이라 같은 이메일의 계정이 이미
     * 있으면 새 users 를 만들지 않는다. 판정은 {@link SocialEmailAccountResolver} 가 맡는다.
     */
    private void beginNewIdentityFlow(HttpServletRequest request,
                                      HttpServletResponse response,
                                      SocialIdentity identity) throws IOException {
        if (socialEmailAccountResolver == null) {
            beginSignup(request, response, identity);
            return;
        }
        SocialEmailAccountResolver.Resolution resolution = socialEmailAccountResolver.resolve(
                identity.provider(), identity.providerEmail(), identity.providerEmailVerified());
        switch (resolution.type()) {
            // Kakao/Naver 는 아직 이 정책을 적용하지 않는다.
            case NOT_APPLICABLE, NEW_ACCOUNT -> beginSignup(request, response, identity);
            case LINK_EXISTING -> beginLink(request, response, identity, resolution);
            case UNVERIFIED_EMAIL ->
                    rejectTo(request, response, UNVERIFIED_EMAIL_REDIRECT);
            case VERIFICATION_PENDING ->
                    rejectTo(request, response, VERIFICATION_PENDING_REDIRECT);
            case WITHDRAWAL_PENDING ->
                    rejectTo(request, response, WITHDRAWAL_PENDING_REDIRECT);
            case BLOCKED -> rejectTo(request, response, BLOCKED_EMAIL_REDIRECT);
        }
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
        request.getSession().removeAttribute(PendingSocialLink.SESSION_ATTRIBUTE);
        request.getSession().removeAttribute("userId");
        clearAuthentication(request, response);
        response.sendRedirect("/social-signup");
    }

    /**
     * 대상 회원 id 는 세션에만 둔다. 확인 화면과 확인 POST 는 이 문맥만 보고 동작한다.
     * 아직 아무것도 저장하지 않으며, 실제 연결은 사용자가 확인 버튼을 눌러야 일어난다.
     */
    private void beginLink(HttpServletRequest request,
                           HttpServletResponse response,
                           SocialIdentity identity,
                           SocialEmailAccountResolver.Resolution resolution) throws IOException {
        Instant createdAt = Instant.now();
        PendingSocialLink pending = new PendingSocialLink(
                UUID.randomUUID().toString(),
                identity.provider(),
                identity.providerUserId(),
                resolution.email(),
                resolution.existingUserId(),
                createdAt,
                createdAt.plus(LINK_TTL));

        request.getSession().setAttribute(PendingSocialLink.SESSION_ATTRIBUTE, pending);
        request.getSession().removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        request.getSession().removeAttribute("userId");
        clearAuthentication(request, response);
        response.sendRedirect("/social-link");
    }

    private void reject(HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        rejectTo(request, response, OAuth2LoginFailureHandler.FAILURE_REDIRECT);
    }

    private void rejectTo(HttpServletRequest request,
                          HttpServletResponse response,
                          String redirect) throws IOException {
        request.getSession().removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        request.getSession().removeAttribute(PendingSocialLink.SESSION_ATTRIBUTE);
        request.getSession().removeAttribute("userId");
        clearAuthentication(request, response);
        response.sendRedirect(redirect);
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
