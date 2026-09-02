package com.example.travlediary.service.user;

import com.example.travlediary.model.SocialProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class RestSocialProviderUnlinkClient implements SocialProviderUnlinkClient {

    private static final String GOOGLE_REVOKE_URI =
            "https://oauth2.googleapis.com/revoke";
    private static final String KAKAO_UNLINK_URI =
            "https://kapi.kakao.com/v1/user/unlink";
    private static final String NAVER_REVOKE_URI =
            "https://nid.naver.com/oauth2.0/revoke";

    private final RestClient restClient;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    public RestSocialProviderUnlinkClient(
            ClientRegistrationRepository clientRegistrationRepository) {
        this(createRestClient(), clientRegistrationRepository);
    }

    RestSocialProviderUnlinkClient(RestClient restClient,
                                   ClientRegistrationRepository clientRegistrationRepository) {
        this.restClient = restClient;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Override
    public void unlink(SocialProvider provider,
                       String accessToken,
                       String providerUserId) {
        if (provider == null || isBlank(accessToken) || isBlank(providerUserId)) {
            throw new SocialProviderUnlinkException();
        }
        try {
            switch (provider) {
                case GOOGLE -> revokeGoogle(accessToken);
                case KAKAO -> unlinkKakao(accessToken, providerUserId);
                case NAVER -> revokeNaver(accessToken);
            }
        } catch (SocialProviderUnlinkException exception) {
            throw exception;
        } catch (RestClientException | IllegalStateException exception) {
            throw new SocialProviderUnlinkException(exception);
        }
    }

    private void revokeGoogle(String accessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", accessToken);
        ResponseEntity<Void> response = restClient.post()
                .uri(GOOGLE_REVOKE_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
        requireOk(response.getStatusCode().value());
    }

    private void unlinkKakao(String accessToken, String providerUserId) {
        ResponseEntity<Map> response = restClient.post()
                .uri(KAKAO_UNLINK_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(Map.class);
        requireOk(response.getStatusCode().value());

        Map<?, ?> body = response.getBody();
        Object returnedId = body == null ? null : body.get("id");
        if (returnedId == null || !providerUserId.equals(String.valueOf(returnedId))) {
            throw new SocialProviderUnlinkException();
        }
    }

    private void revokeNaver(String accessToken) {
        ClientRegistration registration = clientRegistrationRepository
                .findByRegistrationId("naver");
        if (registration == null
                || isBlank(registration.getClientId())
                || isBlank(registration.getClientSecret())) {
            throw new SocialProviderUnlinkException();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", registration.getClientId());
        form.add("client_secret", registration.getClientSecret());
        form.add("token", accessToken);
        form.add("token_type_hint", "access_token");
        ResponseEntity<Void> response = restClient.post()
                .uri(NAVER_REVOKE_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
        requireOk(response.getStatusCode().value());
    }

    private void requireOk(int statusCode) {
        if (statusCode != HttpStatus.OK.value()) {
            throw new SocialProviderUnlinkException();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static RestClient createRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
