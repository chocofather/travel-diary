package com.example.travlediary.config;

import com.example.travlediary.controller.user.LoginController;
import com.example.travlediary.controller.user.SocialSignupController;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.user.SocialSignupAuthenticationService;
import com.example.travlediary.service.user.SocialSignupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Collections;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({LoginController.class, SocialSignupController.class})
@Import(SecurityConfig.class)
@ImportAutoConfiguration(OAuth2ClientAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
        "spring.security.oauth2.client.registration.google.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.kakao.client-id=test-kakao-client",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-kakao-secret",
        "spring.security.oauth2.client.registration.kakao.client-authentication-method=client_secret_post",
        "spring.security.oauth2.client.registration.kakao.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.kakao.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.registration.kakao.scope=openid",
        "spring.security.oauth2.client.registration.kakao.provider=kakao",
        "spring.security.oauth2.client.provider.kakao.authorization-uri=https://kauth.kakao.com/oauth/authorize",
        "spring.security.oauth2.client.provider.kakao.token-uri=https://kauth.kakao.com/oauth/token",
        "spring.security.oauth2.client.provider.kakao.jwk-set-uri=https://kauth.kakao.com/.well-known/jwks.json",
        "spring.security.oauth2.client.provider.kakao.user-info-uri=https://kapi.kakao.com/v1/oidc/userinfo",
        "spring.security.oauth2.client.provider.kakao.user-name-attribute=sub",
        "spring.security.oauth2.client.registration.naver.client-id=test-naver-client",
        "spring.security.oauth2.client.registration.naver.client-secret=test-naver-secret",
        "spring.security.oauth2.client.registration.naver.client-authentication-method=client_secret_post",
        "spring.security.oauth2.client.registration.naver.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.naver.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.registration.naver.provider=naver",
        "spring.security.oauth2.client.provider.naver.authorization-uri=https://nid.naver.com/oauth2.0/authorize",
        "spring.security.oauth2.client.provider.naver.token-uri=https://nid.naver.com/oauth2.0/token",
        "spring.security.oauth2.client.provider.naver.user-info-uri=https://openapi.naver.com/v1/nid/me",
        "spring.security.oauth2.client.provider.naver.user-name-attribute=response"
})
class GoogleOAuthSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private SocialSignupService socialSignupService;
    @MockitoBean
    private SocialSignupAuthenticationService socialSignupAuthenticationService;

    @Test
    void googleAuthorizationEntryUsesSpringSecurityStateNonceAndDefaultCallback()
            throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        OAuth2AuthorizationRequest authorizationRequest = Collections.list(
                        session.getAttributeNames()).stream()
                .map(session::getAttribute)
                .filter(OAuth2AuthorizationRequest.class::isInstance)
                .map(OAuth2AuthorizationRequest.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(authorizationRequest.getClientId()).isEqualTo("test-google-client");
        assertThat(authorizationRequest.getScopes())
                .containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(authorizationRequest.getState()).isNotBlank();
        assertThat(authorizationRequest.getAdditionalParameters().get("nonce"))
                .isInstanceOf(String.class)
                .asString()
                .isNotBlank();
        assertThat(authorizationRequest.getAdditionalParameters())
                .doesNotContainKey("prompt");
        assertThat(authorizationRequest.getRedirectUri())
                .isEqualTo("http://localhost/login/oauth2/code/google");
    }

    @Test
    void kakaoAuthorizationEntryUsesMinimalOidcScopeClientSecretPostAndDefaultCallback()
            throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith("https://kauth.kakao.com/oauth/authorize");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        OAuth2AuthorizationRequest authorizationRequest = Collections.list(
                        session.getAttributeNames()).stream()
                .map(session::getAttribute)
                .filter(OAuth2AuthorizationRequest.class::isInstance)
                .map(OAuth2AuthorizationRequest.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(authorizationRequest.getClientId()).isEqualTo("test-kakao-client");
        assertThat(authorizationRequest.getScopes()).containsExactly("openid");
        assertThat(authorizationRequest.getState()).isNotBlank();
        assertThat(authorizationRequest.getAdditionalParameters().get("nonce"))
                .isInstanceOf(String.class)
                .asString()
                .isNotBlank();
        assertThat(authorizationRequest.getAdditionalParameters())
                .doesNotContainKey("prompt");
        assertThat(authorizationRequest.getRedirectUri())
                .isEqualTo("http://localhost/login/oauth2/code/kakao");
        assertThat(clientRegistrationRepository.findByRegistrationId("kakao")
                .getClientAuthenticationMethod())
                .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
    }

    @Test
    void naverAuthorizationEntryUsesStandardOAuth2StateAndDefaultCallback()
            throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/naver"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith("https://nid.naver.com/oauth2.0/authorize");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        OAuth2AuthorizationRequest authorizationRequest = Collections.list(
                        session.getAttributeNames()).stream()
                .map(session::getAttribute)
                .filter(OAuth2AuthorizationRequest.class::isInstance)
                .map(OAuth2AuthorizationRequest.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(authorizationRequest.getClientId()).isEqualTo("test-naver-client");
        assertThat(authorizationRequest.getState()).isNotBlank();
        assertThat(authorizationRequest.getAdditionalParameters()).doesNotContainKey("nonce");
        assertThat(authorizationRequest.getAdditionalParameters())
                .doesNotContainKey("auth_type");
        assertThat(authorizationRequest.getRedirectUri())
                .isEqualTo("http://localhost/login/oauth2/code/naver");
        assertThat(clientRegistrationRepository.findByRegistrationId("naver")
                .getClientAuthenticationMethod())
                .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
    }

    @Test
    void socialWithdrawalAddsOnlyOfficialProviderReauthenticationParameters()
            throws Exception {
        assertWithdrawalParameter(
                SocialProvider.GOOGLE, "google", "prompt", "select_account");
        assertWithdrawalParameter(
                SocialProvider.KAKAO, "kakao", "prompt", "login");
        assertWithdrawalParameter(
                SocialProvider.NAVER, "naver", "auth_type", "reauthenticate");
    }

    @Test
    void missingSignupStateReachesControllerAndReturnsToLogin() throws Exception {
        mockMvc.perform(get("/social-signup"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?socialSignupExpired=true"));
    }

    @Test
    void socialSignupPostRequiresCsrf() throws Exception {
        mockMvc.perform(post("/social-signup")
                        .param("nickname", "여행자123")
                        .param("termsAccepted", "true")
                        .param("privacyAccepted", "true"))
                .andExpect(status().isForbidden());
    }

    private void assertWithdrawalParameter(SocialProvider provider,
                                           String registrationId,
                                           String parameter,
                                           String expectedValue) throws Exception {
        Instant now = Instant.now();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);
        session.setAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE,
                new PendingSocialWithdrawal(
                        "flow-id", 7L, provider, now, now.plusSeconds(600)));

        MvcResult result = mockMvc.perform(
                        get("/oauth2/authorization/" + registrationId).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        OAuth2AuthorizationRequest authorizationRequest = Collections.list(
                        result.getRequest().getSession(false).getAttributeNames()).stream()
                .map(name -> result.getRequest().getSession(false).getAttribute(name))
                .filter(OAuth2AuthorizationRequest.class::isInstance)
                .map(OAuth2AuthorizationRequest.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(authorizationRequest.getAdditionalParameters())
                .containsEntry(parameter, expectedValue);
        assertThat(authorizationRequest.getState()).isNotBlank();
        if (provider != SocialProvider.NAVER) {
            assertThat(authorizationRequest.getAdditionalParameters().get("nonce"))
                    .isInstanceOf(String.class);
        }
    }

    @Test
    void validSignupPageContainsOnlyAllowedInputsAndCsrfToken() throws Exception {
        Instant now = Instant.now();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE,
                new PendingSocialSignup(
                        "flow-secret", SocialProvider.GOOGLE, "google-sub-secret",
                        "new@example.com", true,
                        now.minusSeconds(10), now.plusSeconds(590)));

        mockMvc.perform(get("/social-signup").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"nickname\"")))
                .andExpect(content().string(containsString("name=\"termsAccepted\"")))
                .andExpect(content().string(containsString("name=\"privacyAccepted\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("google-sub-secret"))))
                .andExpect(content().string(not(containsString("flow-secret"))));
    }

    @Test
    void socialSignupPostWithCsrfReachesPendingValidation() throws Exception {
        mockMvc.perform(post("/social-signup")
                        .with(csrf())
                        .param("nickname", "여행자123")
                        .param("termsAccepted", "true")
                        .param("privacyAccepted", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?socialSignupExpired=true"));
    }

    @Test
    void postedProviderIdentityIsIgnoredInFavorOfServerSessionPending() throws Exception {
        Instant now = Instant.now();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE,
                new PendingSocialSignup(
                        "flow", SocialProvider.GOOGLE, "trusted-session-sub",
                        "trusted@example.com", true,
                        now.minusSeconds(10), now.plusSeconds(590)));
        when(socialSignupService.complete(any(), any())).thenReturn(41L);

        mockMvc.perform(post("/social-signup")
                        .with(csrf())
                        .session(session)
                        .param("nickname", "여행자123")
                        .param("termsAccepted", "true")
                        .param("privacyAccepted", "true")
                        .param("providerUserId", "attacker-sub")
                        .param("providerEmail", "attacker@example.com"))
                .andExpect(status().isOk());

        verify(socialSignupService).complete(
                argThat(pending -> "trusted-session-sub".equals(pending.providerUserId())
                        && "trusted@example.com".equals(pending.providerEmail())),
                any());
    }

    @Test
    void oauthFailureMessageDoesNotExposeTechnicalDetails() throws Exception {
        mockMvc.perform(get("/login").param("oauthError", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "소셜 로그인에 실패했습니다. 다시 시도해 주세요.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("access_denied"))));
    }

    @Test
    void loginPageOffersCompactThreeColumnSocialEntriesWithAccessibleNames()
            throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "href=\"/oauth2/authorization/google\"")))
                .andExpect(content().string(containsString(
                        "href=\"/oauth2/authorization/kakao\"")))
                .andExpect(content().string(containsString(
                        "href=\"/oauth2/authorization/naver\"")))
                .andExpect(content().string(containsString("aria-label=\"Google로 로그인\"")))
                .andExpect(content().string(containsString("aria-label=\"카카오로 로그인\"")))
                .andExpect(content().string(containsString("aria-label=\"네이버로 로그인\"")))
                .andExpect(content().string(containsString(
                        "class=\"social-login-provider__label\">Google")))
                .andExpect(content().string(containsString(
                        "class=\"social-login-provider__label\">카카오")))
                .andExpect(content().string(containsString(
                        "class=\"social-login-provider__label\">네이버")))
                .andExpect(content().string(not(containsString("로 계속하기"))))
                .andExpect(content().string(containsString(
                        "src=\"/images/social/google-g-logo.png\"")))
                .andExpect(content().string(containsString(
                        "src=\"/images/social/kakao-symbol.png\"")))
                .andExpect(content().string(containsString(
                        "src=\"/images/social/naver-symbol.png\"")))
                .andExpect(content().string(containsString(
                        "name=\"username\"")))
                .andExpect(content().string(containsString(
                        "name=\"password\"")));
    }

    @Test
    void kakaoLocalIconContainsVisibleOfficialSymbolPixels() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream(
                "/static/images/social/kakao-symbol.png")) {
            BufferedImage image = ImageIO.read(inputStream);

            assertThat(image).isNotNull();
            assertThat(image.getWidth()).isGreaterThan(0);
            assertThat(image.getHeight()).isGreaterThan(0);
            assertThat(hasVisiblePixel(image)).isTrue();
        }
    }

    private boolean hasVisiblePixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
