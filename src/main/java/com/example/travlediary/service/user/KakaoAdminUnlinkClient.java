package com.example.travlediary.service.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * 서버 배치 전용 카카오 연결 해제.
 *
 * <p>최종 파기는 회원이 로그인하지 않은 30일 뒤에 일어나므로 사용자 access token 을 쓸 수 없다.
 * 그래서 저장해 둔 회원번호와 Admin Key 로 해제한다. 사용자 재인증 흐름이 쓰는
 * {@link RestSocialProviderUnlinkClient}(access token 기반)와는 역할이 달라 따로 둔다.
 */
@Component
public class KakaoAdminUnlinkClient {

    private static final String UNLINK_URI = "https://kapi.kakao.com/v1/user/unlink";
    private static final String TARGET_ID_TYPE = "user_id";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final String adminKey;

    @Autowired
    public KakaoAdminUnlinkClient(@Value("${kakao.admin-key:}") String adminKey) {
        this(createRestClient(), adminKey);
    }

    KakaoAdminUnlinkClient(RestClient restClient, String adminKey) {
        this.restClient = restClient;
        this.adminKey = adminKey == null ? "" : adminKey.strip();
    }

    /**
     * @param providerUserId social_accounts 에 저장해 둔 카카오 회원번호
     * @throws KakaoAdminUnlinkException 설정 오류, 형식 오류, 통신 실패, 응답 불일치
     */
    public void unlinkByUserId(String providerUserId) {
        long targetId = parseTargetId(providerUserId);
        if (adminKey.isEmpty()) {
            throw new KakaoAdminUnlinkException(KakaoAdminUnlinkException.Kind.CONFIGURATION);
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", TARGET_ID_TYPE);
        form.add("target_id", String.valueOf(targetId));

        ResponseEntity<Map> response;
        try {
            response = restClient.post()
                    .uri(UNLINK_URI)
                    // Admin Key 는 여기서만 쓰이고 어떤 로그에도 남기지 않는다.
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
                    .contentType(new MediaType(MediaType.APPLICATION_FORM_URLENCODED,
                            java.nio.charset.StandardCharsets.UTF_8))
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, errorResponse) -> {
                        throw new KakaoAdminUnlinkException(
                                errorResponse.getStatusCode().is5xxServerError()
                                        ? KakaoAdminUnlinkException.Kind.HTTP_5XX
                                        : KakaoAdminUnlinkException.Kind.HTTP_4XX);
                    })
                    .toEntity(Map.class);
        } catch (KakaoAdminUnlinkException exception) {
            throw exception;
        } catch (RestClientException exception) {
            // 연결 실패와 읽기 타임아웃은 같은 예외 계층으로 온다. 둘 다 다시 시도한다.
            throw new KakaoAdminUnlinkException(KakaoAdminUnlinkException.Kind.TIMEOUT);
        }

        requireMatchingId(response.getBody(), targetId);
    }

    /**
     * 돌려받은 id 가 해제를 요청한 회원번호와 같아야 성공이다.
     * 200 만 보고 끝내면 다른 회원이 해제된 응답도 성공으로 읽힌다.
     */
    private void requireMatchingId(Map<?, ?> body, long targetId) {
        Object returnedId = body == null ? null : body.get("id");
        if (returnedId == null) {
            throw new KakaoAdminUnlinkException(
                    KakaoAdminUnlinkException.Kind.RESPONSE_MISMATCH);
        }
        String normalized = returnedId instanceof Number number
                ? String.valueOf(number.longValue())
                : String.valueOf(returnedId).strip();
        if (!String.valueOf(targetId).equals(normalized)) {
            throw new KakaoAdminUnlinkException(
                    KakaoAdminUnlinkException.Kind.RESPONSE_MISMATCH);
        }
    }

    /** 카카오 회원번호는 Long 이다. 형식이 아니면 요청 자체를 보내지 않는다. */
    private long parseTargetId(String providerUserId) {
        String value = providerUserId == null ? "" : providerUserId.strip();
        if (value.isEmpty()) {
            throw new KakaoAdminUnlinkException(
                    KakaoAdminUnlinkException.Kind.INVALID_PROVIDER_USER_ID);
        }
        try {
            long targetId = Long.parseLong(value);
            if (targetId <= 0) {
                throw new KakaoAdminUnlinkException(
                        KakaoAdminUnlinkException.Kind.INVALID_PROVIDER_USER_ID);
            }
            return targetId;
        } catch (NumberFormatException exception) {
            throw new KakaoAdminUnlinkException(
                    KakaoAdminUnlinkException.Kind.INVALID_PROVIDER_USER_ID);
        }
    }

    /** 외부 API 가 무한정 붙들지 않도록 기존 unlink client 와 같은 타임아웃을 쓴다. */
    private static RestClient createRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
