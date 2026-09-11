package com.example.travlediary.service.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Admin Key 기반 배치 연결 해제의 계약. 실제 카카오 서버를 부르지 않는다. */
class KakaoAdminUnlinkClientTest {

    private static final String ADMIN_KEY = "test-admin-key";

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private KakaoAdminUnlinkClient client(String adminKey) {
        return new KakaoAdminUnlinkClient(builder.build(), adminKey);
    }

    @Test
    void theOfficialAdminKeyUnlinkRequestIsSent() {
        server.expect(once(), requestTo("https://kapi.kakao.com/v1/user/unlink"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK " + ADMIN_KEY))
                .andExpect(header(HttpHeaders.CONTENT_TYPE,
                        containsString(MediaType.APPLICATION_FORM_URLENCODED_VALUE)))
                .andExpect(content().string(containsString("target_id_type=user_id")))
                .andExpect(content().string(containsString("target_id=123456789")))
                .andRespond(withSuccess("{\"id\":123456789}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> client(ADMIN_KEY).unlinkByUserId("123456789"))
                .doesNotThrowAnyException();
        server.verify();
    }

    /** 200 만 보고 끝내면 다른 회원이 해제된 응답도 성공으로 읽힌다. */
    @Test
    void aMismatchedReturnedIdIsNotASuccess() {
        server.expect(once(), anything())
                .andRespond(withSuccess("{\"id\":999}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(ADMIN_KEY).unlinkByUserId("123456789"))
                .isInstanceOfSatisfying(KakaoAdminUnlinkException.class, exception -> {
                    assertThat(exception.getKind())
                            .isEqualTo(KakaoAdminUnlinkException.Kind.RESPONSE_MISMATCH);
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    @Test
    void aResponseWithoutAnIdIsNotASuccess() {
        server.expect(once(), anything())
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(ADMIN_KEY).unlinkByUserId("123456789"))
                .isInstanceOf(KakaoAdminUnlinkException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "not-a-number", "12.5", "-1", "0",
            "99999999999999999999999"})
    void anInvalidProviderUserIdNeverReachesTheApi(String providerUserId) {
        server.expect(never(), anything());

        assertThatThrownBy(() -> client(ADMIN_KEY).unlinkByUserId(providerUserId))
                .isInstanceOfSatisfying(KakaoAdminUnlinkException.class, exception -> {
                    assertThat(exception.getKind())
                            .isEqualTo(KakaoAdminUnlinkException.Kind.INVALID_PROVIDER_USER_ID);
                    assertThat(exception.isRetryable()).isFalse();
                });
        server.verify();
    }

    @Test
    void aNullProviderUserIdNeverReachesTheApi() {
        server.expect(never(), anything());

        assertThatThrownBy(() -> client(ADMIN_KEY).unlinkByUserId(null))
                .isInstanceOf(KakaoAdminUnlinkException.class);
        server.verify();
    }

    /** Admin Key 가 없으면 요청을 보내지 않고, 값을 넣으면 풀리는 실패라 재시도 대상이다. */
    @Test
    void aMissingAdminKeyFailsBeforeAnyRequestAndIsRetryable() {
        server.expect(never(), anything());

        assertThatThrownBy(() -> client("").unlinkByUserId("123456789"))
                .isInstanceOfSatisfying(KakaoAdminUnlinkException.class, exception -> {
                    assertThat(exception.getKind())
                            .isEqualTo(KakaoAdminUnlinkException.Kind.CONFIGURATION);
                    assertThat(exception.isRetryable()).isTrue();
                });
        server.verify();
    }

    @Test
    void serverErrorsAreRetried() {
        server.expect(once(), anything())
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client(ADMIN_KEY).unlinkByUserId("123456789"))
                .isInstanceOfSatisfying(KakaoAdminUnlinkException.class, exception -> {
                    assertThat(exception.getKind())
                            .isEqualTo(KakaoAdminUnlinkException.Kind.HTTP_5XX);
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    /**
     * 4xx 는 이미 해제된 회원인지 권한 문제인지 공식 오류코드 대조 없이 구분할 수 없다.
     * 성공으로 넘기지 않고, 재시도해도 같으므로 운영 확인 대상으로 남긴다.
     */
    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404})
    void clientErrorsAreNeverTreatedAsSuccessAndAreNotRetried(int status) {
        server.expect(once(), anything())
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .body("{\"msg\":\"...\",\"code\":-101}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(ADMIN_KEY).unlinkByUserId("123456789"))
                .isInstanceOfSatisfying(KakaoAdminUnlinkException.class, exception -> {
                    assertThat(exception.getKind())
                            .isEqualTo(KakaoAdminUnlinkException.Kind.HTTP_4XX);
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    /** last_error 로 저장되는 값이라 키와 회원번호가 섞이면 안 된다. */
    @Test
    void theErrorCodeNeverCarriesTheAdminKeyOrTheProviderUserId() {
        server.expect(once(), anything())
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"msg\":\"invalid app admin key " + ADMIN_KEY + "\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(ADMIN_KEY).unlinkByUserId("123456789"))
                .isInstanceOfSatisfying(KakaoAdminUnlinkException.class, exception ->
                        assertThat(exception.errorCode())
                                .contains("HTTP_4XX")
                                .doesNotContain(ADMIN_KEY, "123456789", "KakaoAK", "invalid app"));
    }

    @Test
    void everyErrorCodeIsSafeToStore() {
        for (KakaoAdminUnlinkException.Kind kind : KakaoAdminUnlinkException.Kind.values()) {
            assertThat(new KakaoAdminUnlinkException(kind).errorCode())
                    .as(kind.name())
                    .startsWith(kind.name())
                    .doesNotContain("KakaoAK", "Authorization", "target_id");
        }
    }
}
