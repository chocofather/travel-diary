package com.example.travlediary.config;

import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.SocialAccountService;
import com.example.travlediary.service.user.UserSanctionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialOAuth2LoginSuccessHandlerTest {

    @Mock
    private SocialAccountService socialAccountService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserSanctionService userSanctionService;

    private SocialOAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SocialOAuth2LoginSuccessHandler(
                socialAccountService,
                userMapper,
                userSanctionService,
                new CustomLoginSuccessHandler(userMapper));
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeGoogleAccountUsesSubAndBecomesCustomUserDetailsWithDatabaseUserRole()
            throws Exception {
        OAuth2AuthenticationToken googleAuthentication = googleAuthentication(
                "google-sub-123", "same@example.com", true, "ROLE_ADMIN");
        SocialAccount socialAccount = socialAccount(7L, "google-sub-123");
        User user = user(7L, null, UserRole.USER, UserStatus.ACTIVE);
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "google-sub-123")).thenReturn(socialAccount);
        when(userMapper.findById(7L)).thenReturn(user);
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.ACTIVE);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("redirect", "/mypage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, googleAuthentication);

        Authentication internal = savedAuthentication(request);
        assertThat(internal).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(internal.getPrincipal()).isInstanceOf(CustomUserDetails.class);
        assertThat(((CustomUserDetails) internal.getPrincipal()).getId()).isEqualTo(7L);
        assertThat(internal.getName()).isEqualTo("user:7");
        assertThat(internal.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(7L);
        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage");
        verify(socialAccountService).findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "google-sub-123");
        verify(userMapper).findById(7L);
        verify(userMapper, never()).findByEmail(anyString());
    }

    @Test
    void databaseAdminRoleIsUsedEvenWhenGoogleDoesNotProvideIt() throws Exception {
        OAuth2AuthenticationToken googleAuthentication = googleAuthentication(
                "admin-sub", "admin@example.com", true, "OIDC_USER");
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "admin-sub"))
                .thenReturn(socialAccount(99L, "admin-sub"));
        when(userMapper.findById(99L))
                .thenReturn(user(99L, "admin", UserRole.ADMIN, UserStatus.ACTIVE));
        when(userMapper.findStatusById(99L)).thenReturn(UserStatus.ACTIVE);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, googleAuthentication);

        assertThat(savedAuthentication(request).getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void savedRequestPolicyIsReusedForAnExistingGoogleAccount() throws Exception {
        OAuth2AuthenticationToken authentication = googleAuthentication(
                "google-sub-123", "member@example.com", true, "OIDC_USER");
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "google-sub-123"))
                .thenReturn(socialAccount(7L, "google-sub-123"));
        when(userMapper.findById(7L))
                .thenReturn(user(7L, null, UserRole.USER, UserStatus.ACTIVE));
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.ACTIVE);
        MockHttpServletRequest original = new MockHttpServletRequest("GET", "/travel-info");
        original.setScheme("http");
        original.setServerName("localhost");
        original.setServerPort(80);
        original.setQueryString("sort=views");
        new HttpSessionRequestCache().saveRequest(original, new MockHttpServletResponse());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(original.getSession());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/travel-info?sort=views");
    }

    @Test
    void restrictedGoogleAccountUsesTheExistingRestrictedRedirect() throws Exception {
        OAuth2AuthenticationToken authentication = googleAuthentication(
                "restricted-sub", "member@example.com", true, "OIDC_USER");
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "restricted-sub"))
                .thenReturn(socialAccount(7L, "restricted-sub"));
        when(userMapper.findById(7L))
                .thenReturn(user(7L, null, UserRole.USER, UserStatus.RESTRICTED));
        when(userSanctionService.releaseIfExpired(7L)).thenReturn(false);
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.RESTRICTED);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/account/restricted");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(7L);
        assertThat(savedAuthentication(request).getPrincipal())
                .isInstanceOf(CustomUserDetails.class);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"INACTIVE", "SUSPENDED", "DEACTIVATED"})
    void unavailableGoogleAccountStatusesAreRejected(UserStatus status) throws Exception {
        OAuth2AuthenticationToken authentication = googleAuthentication(
                "blocked-sub", "member@example.com", true, "OIDC_USER");
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "blocked-sub"))
                .thenReturn(socialAccount(7L, "blocked-sub"));
        when(userMapper.findById(7L))
                .thenReturn(user(7L, null, UserRole.USER, status));
        MockHttpServletRequest request = new MockHttpServletRequest();
        saveAuthentication(request, authentication);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=true");
        assertThat(request.getSession().getAttribute("userId")).isNull();
        assertThat(request.getSession().getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    @Test
    void unknownGoogleAccountStoresOnlyShortLivedVerifiedSignupData() throws Exception {
        OAuth2AuthenticationToken authentication = googleAuthentication(
                "new-google-sub", "new@example.com", true, "ROLE_ADMIN");
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "new-google-sub")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        saveAuthentication(request, authentication);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        PendingSocialSignup pending = (PendingSocialSignup) request.getSession()
                .getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        assertThat(pending.flowId()).isNotBlank();
        assertThat(pending.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(pending.providerUserId()).isEqualTo("new-google-sub");
        assertThat(pending.providerEmail()).isEqualTo("new@example.com");
        assertThat(pending.providerEmailVerified()).isTrue();
        assertThat(Duration.between(pending.createdAt(), pending.expiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(response.getRedirectedUrl()).isEqualTo("/social-signup");
        assertThat(Collections.list(request.getSession().getAttributeNames()))
                .containsExactly(PendingSocialSignup.SESSION_ATTRIBUTE);
        verify(userMapper, never()).findByEmail(anyString());
        verify(userMapper, never()).insertUser(any());
        verify(socialAccountService, never()).connect(any());
    }

    @Test
    void unknownGoogleAccountPreservesFalseEmailVerificationWithoutUsingEmailAsIdentity()
            throws Exception {
        OAuth2AuthenticationToken authentication = googleAuthentication(
                "sub-not-email", "existing@example.com", false, "OIDC_USER");
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "sub-not-email")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        PendingSocialSignup pending = (PendingSocialSignup) request.getSession()
                .getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        assertThat(pending.providerEmailVerified()).isFalse();
        assertThat(pending.providerUserId()).isEqualTo("sub-not-email");
        verify(socialAccountService).findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "sub-not-email");
        verify(userMapper, never()).findByEmail("existing@example.com");
    }

    @Test
    void activeKakaoAccountUsesSubAndDatabaseRoleInsteadOfProviderAuthority()
            throws Exception {
        OAuth2AuthenticationToken authentication = oidcAuthentication(
                "kakao", "https://kauth.kakao.com", "kakao-sub-123",
                "same@example.com", true, "ROLE_ADMIN");
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "kakao-sub-123"))
                .thenReturn(socialAccount(17L, SocialProvider.KAKAO, "kakao-sub-123"));
        when(userMapper.findById(17L))
                .thenReturn(user(17L, null, UserRole.USER, UserStatus.ACTIVE));
        when(userMapper.findStatusById(17L)).thenReturn(UserStatus.ACTIVE);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        Authentication internal = savedAuthentication(request);
        assertThat(((CustomUserDetails) internal.getPrincipal()).getId()).isEqualTo(17L);
        assertThat(internal.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(17L);
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
        assertThat(request.getSession().getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE))
                .isNull();
        verify(socialAccountService).findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "kakao-sub-123");
        verify(userMapper, never()).insertUser(any());
        verify(userMapper, never()).findByEmail(anyString());
    }

    @Test
    void unknownKakaoAccountWithoutEmailCreatesValidMinimalPendingSignup()
            throws Exception {
        OAuth2AuthenticationToken authentication = oidcAuthentication(
                "kakao", "https://kauth.kakao.com", "new-kakao-sub",
                null, null, "OIDC_USER");
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "new-kakao-sub")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        PendingSocialSignup pending = (PendingSocialSignup) request.getSession()
                .getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        assertThat(pending.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(pending.providerUserId()).isEqualTo("new-kakao-sub");
        assertThat(pending.providerEmail()).isNull();
        assertThat(pending.providerEmailVerified()).isNull();
        assertThat(Duration.between(pending.createdAt(), pending.expiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(response.getRedirectedUrl()).isEqualTo("/social-signup");
        verify(userMapper, never()).insertUser(any());
        verify(socialAccountService, never()).connect(any());
    }

    @Test
    void unavailableKakaoAccountUsesTheExistingFailurePolicy() throws Exception {
        OAuth2AuthenticationToken authentication = oidcAuthentication(
                "kakao", "https://kauth.kakao.com", "suspended-kakao-sub",
                null, null, "OIDC_USER");
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "suspended-kakao-sub"))
                .thenReturn(socialAccount(
                        17L, SocialProvider.KAKAO, "suspended-kakao-sub"));
        when(userMapper.findById(17L))
                .thenReturn(user(17L, null, UserRole.USER, UserStatus.SUSPENDED));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=true");
        assertThat(request.getSession().getAttribute("userId")).isNull();
    }

    @Test
    void activeNaverAccountUsesNestedResponseIdAndDatabaseRole() throws Exception {
        OAuth2AuthenticationToken authentication = naverAuthentication(
                "00", Map.of("id", "naver-id-123", "email", "same@example.com"),
                "ROLE_ADMIN", true);
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.NAVER, "naver-id-123"))
                .thenReturn(socialAccount(27L, SocialProvider.NAVER, "naver-id-123"));
        when(userMapper.findById(27L))
                .thenReturn(user(27L, null, UserRole.USER, UserStatus.ACTIVE));
        when(userMapper.findStatusById(27L)).thenReturn(UserStatus.ACTIVE);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        Authentication internal = savedAuthentication(request);
        assertThat(((CustomUserDetails) internal.getPrincipal()).getId()).isEqualTo(27L);
        assertThat(internal.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(27L);
        assertThat(request.getSession().getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE))
                .isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
        verify(socialAccountService).findByProviderAndProviderUserId(
                SocialProvider.NAVER, "naver-id-123");
        verify(userMapper, never()).findByEmail(anyString());
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void unknownNaverAccountStoresEmailAsUnverifiedReferenceOnly() throws Exception {
        OAuth2AuthenticationToken authentication = naverAuthentication(
                "00", Map.of("id", "new-naver-id", "email", "naver@example.com"),
                "ROLE_ADMIN", true);
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.NAVER, "new-naver-id")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        PendingSocialSignup pending = (PendingSocialSignup) request.getSession()
                .getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        assertThat(pending.provider()).isEqualTo(SocialProvider.NAVER);
        assertThat(pending.providerUserId()).isEqualTo("new-naver-id");
        assertThat(pending.providerEmail()).isEqualTo("naver@example.com");
        assertThat(pending.providerEmailVerified()).isNull();
        assertThat(Duration.between(pending.createdAt(), pending.expiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(Collections.list(request.getSession().getAttributeNames()))
                .containsExactly(PendingSocialSignup.SESSION_ATTRIBUTE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/social-signup");
        verify(userMapper, never()).findByEmail(anyString());
        verify(userMapper, never()).insertUser(any());
        verify(socialAccountService, never()).connect(any());
    }

    @Test
    void unknownNaverAccountWithoutEmailStillCreatesPendingSignup() throws Exception {
        OAuth2AuthenticationToken authentication = naverAuthentication(
                "00", Map.of("id", "new-naver-id"), "OAUTH2_USER", true);
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.NAVER, "new-naver-id")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        PendingSocialSignup pending = (PendingSocialSignup) request.getSession()
                .getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        assertThat(pending.provider()).isEqualTo(SocialProvider.NAVER);
        assertThat(pending.providerUserId()).isEqualTo("new-naver-id");
        assertThat(pending.providerEmail()).isNull();
        assertThat(pending.providerEmailVerified()).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/social-signup");
    }

    @Test
    void suspendedNaverAccountUsesTheExistingFailurePolicy() throws Exception {
        OAuth2AuthenticationToken authentication = naverAuthentication(
                "00", Map.of("id", "suspended-naver-id"), "OAUTH2_USER", true);
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.NAVER, "suspended-naver-id"))
                .thenReturn(socialAccount(
                        27L, SocialProvider.NAVER, "suspended-naver-id"));
        when(userMapper.findById(27L))
                .thenReturn(user(27L, null, UserRole.USER, UserStatus.SUSPENDED));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=true");
        assertThat(request.getSession().getAttribute("userId")).isNull();
    }

    @Test
    void naverRejectsFailedResultCodeMissingResponseAndUnexpectedResponseType()
            throws Exception {
        assertNaverRejected(naverAuthentication(
                "01", Map.of("id", "id"), "OAUTH2_USER", true));
        assertNaverRejected(naverAuthentication(
                "00", null, "OAUTH2_USER", false));
        assertNaverRejected(naverAuthentication(
                "00", "not-a-map", "OAUTH2_USER", true));

        verify(socialAccountService, never())
                .findByProviderAndProviderUserId(any(), anyString());
    }

    @Test
    void naverRejectsMissingOrBlankResponseIdWithoutEmailFallback() throws Exception {
        assertNaverRejected(naverAuthentication(
                "00", Map.of("email", "fallback@example.com"), "OAUTH2_USER", true));
        assertNaverRejected(naverAuthentication(
                "00", Map.of("id", "   ", "email", "fallback@example.com"),
                "OAUTH2_USER", true));

        verify(socialAccountService, never())
                .findByProviderAndProviderUserId(any(), anyString());
        verify(userMapper, never()).findByEmail(anyString());
    }

    @Test
    void unsupportedRegistrationIdIsRejectedBeforeSocialAccountLookup() throws Exception {
        OAuth2AuthenticationToken authentication = oidcAuthentication(
                "unsupported", "https://example.com", "unsupported-sub",
                null, null, "OIDC_USER");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=true");
        verify(socialAccountService, never())
                .findByProviderAndProviderUserId(any(), anyString());
    }

    private OAuth2AuthenticationToken googleAuthentication(String sub, String email,
                                                            Boolean emailVerified,
                                                            String authority) {
        return oidcAuthentication(
                "google", "https://accounts.google.com", sub, email,
                emailVerified, authority);
    }

    private OAuth2AuthenticationToken oidcAuthentication(
            String registrationId, String issuer, String sub, String email,
            Boolean emailVerified, String authority) {
        Instant issuedAt = Instant.parse("2026-09-02T00:00:00Z");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("sub", sub);
        claims.put("aud", List.of("test-client"));
        claims.put("iat", issuedAt);
        claims.put("exp", issuedAt.plusSeconds(300));
        if (email != null) {
            claims.put("email", email);
        }
        if (emailVerified != null) {
            claims.put("email_verified", emailVerified);
        }
        OidcIdToken idToken = new OidcIdToken(
                "test-id-token-value", issuedAt, issuedAt.plusSeconds(300), claims);
        OidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority(authority)), idToken, "sub");
        return new OAuth2AuthenticationToken(
                oidcUser, oidcUser.getAuthorities(), registrationId);
    }

    private OAuth2AuthenticationToken naverAuthentication(
            String resultCode, Object response, String authority,
            boolean includeResponse) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("resultcode", resultCode);
        if (includeResponse) {
            attributes.put("response", response);
        }
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority(authority)), attributes, "resultcode");
        return new OAuth2AuthenticationToken(
                oauth2User, oauth2User.getAuthorities(), "naver");
    }

    private void assertNaverRejected(OAuth2AuthenticationToken authentication)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=true");
        assertThat(request.getSession().getAttribute("userId")).isNull();
        assertThat(request.getSession().getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE))
                .isNull();
    }

    private SocialAccount socialAccount(Long userId, String providerUserId) {
        return socialAccount(userId, SocialProvider.GOOGLE, providerUserId);
    }

    private SocialAccount socialAccount(Long userId, SocialProvider provider,
                                        String providerUserId) {
        SocialAccount account = new SocialAccount();
        account.setUserId(userId);
        account.setProvider(provider);
        account.setProviderUserId(providerUserId);
        return account;
    }

    private User user(Long id, String username, UserRole role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setUserRole(role);
        user.setStatus(status);
        return user;
    }

    private void saveAuthentication(MockHttpServletRequest request, Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        request.getSession().setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private Authentication savedAuthentication(MockHttpServletRequest request) {
        SecurityContext context = (SecurityContext) request.getSession().getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).isNotNull();
        return context.getAuthentication();
    }
}
