package com.example.travlediary.config;

import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SocialWithdrawalAuthorizationRequestResolverTest {

    @Test
    void normalLoginKeepsTheDelegatedRequestWithoutWithdrawalParameters() {
        OAuth2AuthorizationRequest original = original("google");
        SocialWithdrawalAuthorizationRequestResolver resolver = resolver(original);
        MockHttpServletRequest request = request("google");

        OAuth2AuthorizationRequest resolved = resolver.resolve(request, "google");

        assertThat(resolved.getState()).isEqualTo("spring-state");
        assertThat(resolved.getAdditionalParameters()).containsEntry("nonce", "spring-nonce");
        assertThat(resolved.getAdditionalParameters()).doesNotContainKeys("prompt", "auth_type");
    }

    @Test
    void addsOnlyTheProviderReauthenticationParameterForAValidWithdrawalIntent() {
        assertWithdrawalParameter(SocialProvider.GOOGLE, "google", "prompt", "select_account");
        assertWithdrawalParameter(SocialProvider.KAKAO, "kakao", "prompt", "login");
        assertWithdrawalParameter(SocialProvider.NAVER, "naver", "auth_type", "reauthenticate");
    }

    @Test
    void mismatchedUserOrProviderDoesNotAddAWithdrawalParameter() {
        OAuth2AuthorizationRequest original = original("google");
        SocialWithdrawalAuthorizationRequestResolver resolver = resolver(original);
        MockHttpServletRequest request = request("google");
        request.getSession().setAttribute("userId", 99L);
        request.getSession().setAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE,
                pending(7L, SocialProvider.GOOGLE));

        OAuth2AuthorizationRequest resolved = resolver.resolve(request, "google");

        assertThat(resolved.getAdditionalParameters()).doesNotContainKey("prompt");
    }

    private void assertWithdrawalParameter(SocialProvider provider,
                                           String registrationId,
                                           String name,
                                           String value) {
        OAuth2AuthorizationRequest original = original(registrationId);
        SocialWithdrawalAuthorizationRequestResolver resolver = resolver(original);
        MockHttpServletRequest request = request(registrationId);
        request.getSession().setAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE,
                pending(7L, provider));

        OAuth2AuthorizationRequest resolved = resolver.resolve(request, registrationId);

        assertThat(resolved.getState()).isEqualTo("spring-state");
        assertThat(resolved.getAdditionalParameters())
                .containsEntry("nonce", "spring-nonce")
                .containsEntry(name, value);
    }

    private SocialWithdrawalAuthorizationRequestResolver resolver(
            OAuth2AuthorizationRequest original) {
        return new SocialWithdrawalAuthorizationRequestResolver(
                new OAuth2AuthorizationRequestResolver() {
                    @Override
                    public OAuth2AuthorizationRequest resolve(
                            jakarta.servlet.http.HttpServletRequest request) {
                        return original;
                    }

                    @Override
                    public OAuth2AuthorizationRequest resolve(
                            jakarta.servlet.http.HttpServletRequest request,
                            String clientRegistrationId) {
                        return original;
                    }
                });
    }

    private OAuth2AuthorizationRequest original(String registrationId) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://provider.example/authorize")
                .clientId("client-id")
                .redirectUri("http://localhost/login/oauth2/code/" + registrationId)
                .scopes(java.util.Set.of("openid"))
                .state("spring-state")
                .additionalParameters(Map.of("nonce", "spring-nonce"))
                .attributes(Map.of("registration_id", registrationId))
                .authorizationRequestUri("https://provider.example/authorize?state=spring-state")
                .build();
    }

    private MockHttpServletRequest request(String registrationId) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/oauth2/authorization/" + registrationId);
        request.getSession().setAttribute("userId", 7L);
        return request;
    }

    private PendingSocialWithdrawal pending(Long userId, SocialProvider provider) {
        Instant now = Instant.now();
        return new PendingSocialWithdrawal(
                "flow-id", userId, provider, now, now.plusSeconds(600));
    }
}
