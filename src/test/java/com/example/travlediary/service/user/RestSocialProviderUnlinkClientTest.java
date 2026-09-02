package com.example.travlediary.service.user;

import com.example.travlediary.model.SocialProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class RestSocialProviderUnlinkClientTest {

    private MockRestServiceServer server;
    private RestSocialProviderUnlinkClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestSocialProviderUnlinkClient(
                builder.build(),
                new InMemoryClientRegistrationRepository(naverRegistration()));
    }

    @Test
    void googlePostsTheAccessTokenToTheOfficialRevokeEndpoint() {
        server.expect(once(), requestTo("https://oauth2.googleapis.com/revoke"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE,
                        containsString(MediaType.APPLICATION_FORM_URLENCODED_VALUE)))
                .andExpect(content().string("token=google-access-token"))
                .andRespond(withStatus(HttpStatus.OK));

        client.unlink(SocialProvider.GOOGLE, "google-access-token", "google-sub");

        server.verify();
    }

    @Test
    void kakaoUsesTheOfficialUnlinkEndpointAndBearerTokenAndChecksReturnedId() {
        server.expect(once(), requestTo("https://kapi.kakao.com/v1/user/unlink"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-access-token"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":123456789}"));

        client.unlink(SocialProvider.KAKAO, "kakao-access-token", "123456789");

        server.verify();
    }

    @Test
    void naverPostsEnvironmentBackedRegistrationCredentialsAndTokenToOfficialEndpoint() {
        server.expect(once(), requestTo("https://nid.naver.com/oauth2.0/revoke"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE,
                        containsString(MediaType.APPLICATION_FORM_URLENCODED_VALUE)))
                .andExpect(content().string(containsString("client_id=naver-client-id")))
                .andExpect(content().string(containsString("client_secret=naver-client-secret")))
                .andExpect(content().string(containsString("token=naver-access-token")))
                .andExpect(content().string(containsString("token_type_hint=access_token")))
                .andRespond(withStatus(HttpStatus.OK));

        client.unlink(SocialProvider.NAVER, "naver-access-token", "naver-id");

        server.verify();
    }

    @Test
    void nonOkProviderResponseFailsClosed() {
        server.expect(once(), requestTo("https://oauth2.googleapis.com/revoke"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.unlink(
                SocialProvider.GOOGLE, "access-token", "google-sub"))
                .isInstanceOf(SocialProviderUnlinkException.class);
    }

    @Test
    void kakaoDifferentReturnedIdentityFailsClosed() {
        server.expect(once(), requestTo("https://kapi.kakao.com/v1/user/unlink"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":999}"));

        assertThatThrownBy(() -> client.unlink(
                SocialProvider.KAKAO, "access-token", "123"))
                .isInstanceOf(SocialProviderUnlinkException.class);
    }

    private ClientRegistration naverRegistration() {
        return ClientRegistration.withRegistrationId("naver")
                .clientId("naver-client-id")
                .clientSecret("naver-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                .tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri("https://openapi.naver.com/v1/nid/me")
                .userNameAttributeName("response")
                .clientName("Naver")
                .build();
    }
}
