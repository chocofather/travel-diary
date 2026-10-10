package com.tripbora.service.pixabay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

/** 실제 Pixabay API는 부르지 않는다. 모든 응답은 MockRestServiceServer 가 돌려준다. */
class PixabayApiClientTest {

    private static final String KEY = "test-key-not-real";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://pixabay.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private PixabayApiClient client(String key) {
        return new PixabayApiClient(builder.build(), new ObjectMapper(), key, CLOCK);
    }

    @Test
    void searchesPhotosOnlyWithSafeSearchAndPopularOrderAndParsesHits() {
        server.expect(requestTo(containsString("/api/")))
                .andExpect(queryParam("key", KEY))
                .andExpect(queryParam("q", "Eiffel%20Tower%20Paris%20France"))
                .andExpect(queryParam("lang", "en"))
                .andExpect(queryParam("image_type", "photo"))
                .andExpect(queryParam("safesearch", "true"))
                .andExpect(queryParam("order", "popular"))
                .andExpect(queryParam("page", "2"))
                .andExpect(queryParam("per_page", "50"))
                .andRespond(withSuccess("""
                        {"totalHits":120,"hits":[
                          {"id":195893,"type":"photo","pageURL":"https://pixabay.com/photos/eiffel-tower-195893/",
                           "previewURL":"https://cdn.pixabay.com/photo/2013/10/15/09/12/eiffel-195893_150.jpg",
                           "webformatURL":"https://pixabay.com/get/35bbf209e13e39d2_640.jpg",
                           "largeImageURL":"https://pixabay.com/get/ed6a99fd0a76647_1280.jpg",
                           "imageWidth":4000,"imageHeight":3000,"user":"Hans","tags":"eiffel, paris"},
                          {"id":7,"type":"photo","pageURL":"https://pixabay.com/photos/full-7/",
                           "largeImageURL":"https://pixabay.com/get/l_1280.jpg",
                           "fullHDURL":"https://pixabay.com/get/f_1920.jpg",
                           "imageURL":"https://pixabay.com/get/original.jpg","user":"Ann"},
                          {"id":8,"type":"illustration","pageURL":"https://pixabay.com/illustrations/x-8/",
                           "largeImageURL":"https://pixabay.com/get/i_1280.jpg"},
                          {"id":9,"type":"photo","pageURL":"https://evil.example/9/",
                           "largeImageURL":"https://pixabay.com/get/e_1280.jpg"},
                          {"id":10,"type":"photo","pageURL":"https://pixabay.com/photos/no-image-10/"}
                        ]}""", MediaType.APPLICATION_JSON));

        PixabaySearchPage page = client(KEY).searchPhotos("Eiffel Tower Paris France", "en", 2, 50);

        server.verify();
        assertThat(page.totalHits()).isEqualTo(120);
        assertThat(page.fetchedAt()).isEqualTo(CLOCK.instant());
        // 사진이 아니거나, 원본 페이지가 Pixabay 가 아니거나, 저장할 이미지 URL이 없는 항목은 버린다.
        assertThat(page.hits()).extracting(PixabayHit::id).containsExactly(195893L, 7L);
        PixabayHit basic = page.hits().get(0);
        assertThat(basic.user()).isEqualTo("Hans");
        assertThat(basic.imageWidth()).isEqualTo(4000);
        assertThat(basic.thumbnailUrl()).isEqualTo("https://pixabay.com/get/35bbf209e13e39d2_640.jpg");
        // 기본 계정 응답에는 imageURL·fullHDURL 이 없다. 없는 URL을 만들지 않고 largeImageURL 만 저장 후보로 쓴다.
        assertThat(basic.downloadCandidates()).extracting(PixabayHit.DownloadCandidate::field)
                .containsExactly("largeImageURL");
        assertThat(page.hits().get(1).downloadCandidates()).extracting(PixabayHit.DownloadCandidate::url)
                .containsExactly("https://pixabay.com/get/original.jpg", "https://pixabay.com/get/f_1920.jpg",
                        "https://pixabay.com/get/l_1280.jpg");
    }

    @Test
    void queryCharactersCannotInjectOtherParameters() {
        server.expect(requestTo(containsString("q=a%26per_page%3D200")))
                .andExpect(queryParam("per_page", "50"))
                .andRespond(withSuccess("{\"totalHits\":0,\"hits\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client(KEY).searchPhotos("a&per_page=200", "en", 1, 50).hits()).isEmpty();
        server.verify();
    }

    @Test
    void missingApiKeyFailsWithoutCallingPixabay() {
        PixabayApiClient client = client("  ");

        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.searchPhotos("Seoul", "en", 1, 50))
                .isInstanceOfSatisfying(PixabayApiException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(PixabayApiException.Reason.NOT_CONFIGURED))
                .hasMessage("Pixabay API Key가 설정되지 않았습니다.");
        server.verify();
    }

    @Test
    void rateLimitIsReportedOnceWithoutRetrying() {
        server.expect(requestTo(containsString("/api/"))).andRespond(withTooManyRequests());

        assertThatThrownBy(() -> client(KEY).searchPhotos("Seoul", "en", 1, 50))
                .isInstanceOfSatisfying(PixabayApiException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(PixabayApiException.Reason.RATE_LIMITED))
                .hasMessage("Pixabay 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
        // 한 번만 불렀다(자동 재시도 없음).
        server.verify();
    }

    @Test
    void timeoutAndUpstreamErrorsBecomeReadableMessagesWithoutTheKey() {
        server.expect(requestTo(containsString("/api/"))).andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(containsString("/api/"))).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(containsString("/api/")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("[ERROR 400] Invalid or missing API key"));
        PixabayApiClient client = client(KEY);

        assertThatThrownBy(() -> client.searchPhotos("Seoul", "en", 1, 50))
                .isInstanceOfSatisfying(PixabayApiException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(PixabayApiException.Reason.TIMEOUT))
                .hasNoCause();
        for (int status : List.of(502, 400)) {
            assertThatThrownBy(() -> client.searchPhotos("Seoul", "en", 1, 50))
                    .isInstanceOfSatisfying(PixabayApiException.class, exception -> {
                        assertThat(exception.reason()).isEqualTo(PixabayApiException.Reason.UPSTREAM);
                        assertThat(exception.getMessage()).contains("HTTP " + status).doesNotContain(KEY);
                    })
                    .hasNoCause();
        }
        server.verify();
    }
}
