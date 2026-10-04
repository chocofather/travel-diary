package com.tripbora.service.wikidata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

class CommonsApiClientTest {
    @Test
    void fetchesOnlyFilesInTheConnectedCategory() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://commons.wikimedia.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CommonsApiClient client = new CommonsApiClient(builder.build(), new ObjectMapper());
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("list", "categorymembers"))
                .andExpect(queryParam("cmtype", "file"))
                // 저장 재검증은 미리보기가 탐색하는 카테고리 앞쪽 범위 전체를 후보로 본다.
                .andExpect(queryParam("cmlimit", "500"))
                .andRespond(withSuccess("{\"query\":{\"categorymembers\":[{\"ns\":6,\"title\":\"File:Tower.jpg\"},{\"ns\":14,\"title\":\"Category:Other\"}]}}", MediaType.APPLICATION_JSON));

        assertThat(client.listCategoryFiles("Eiffel Tower")).isEqualTo(List.of("File:Tower.jpg"));
        server.verify();
    }

    @Test
    void previewCategoryListAndThumbnailMetadataComeFromOneRequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://commons.wikimedia.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CommonsApiClient client = new CommonsApiClient(builder.build(), new ObjectMapper());
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("list", "categorymembers"))
                .andExpect(queryParam("cmlimit", "8"))
                .andExpect(queryParam("generator", "categorymembers"))
                .andExpect(queryParam("gcmlimit", "8"))
                // 라이선스 판별 템플릿도 같은 요청으로 받는다(인코딩된 '|').
                .andExpect(queryParam("prop", "imageinfo%7Ctemplates"))
                .andExpect(queryParam("tllimit", "max"))
                .andExpect(requestTo(containsString("Licensed-PD")))
                .andExpect(queryParam("iiurlwidth", "240"))
                // 미리보기는 판별에 쓰는 extmetadata 항목만 받는다.
                .andExpect(requestTo(containsString("iiextmetadatafilter=Artist")))
                .andRespond(withSuccess("{\"query\":{\"categorymembers\":[{\"ns\":6,\"title\":\"File:A.jpg\"}],"
                        + "\"pages\":[{\"title\":\"File:A.jpg\",\"imageinfo\":[{}]}]}}", MediaType.APPLICATION_JSON));
        // 다음 묶음은 목록과 파일 정보를 같은 위치에서 이어 받는다.
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("cmcontinue", "file%7Cab%7C1"))
                .andExpect(queryParam("gcmcontinue", "file%7Cab%7C1"))
                .andRespond(withSuccess("{\"query\":{}}", MediaType.APPLICATION_JSON));

        assertThat(client.getCategoryImageInfo("Eiffel Tower", 8, null).path("query").path("pages")).hasSize(1);
        assertThatThrownBy(() -> client.getCategoryImageInfo("Eiffel Tower", 8, "file|ab|1"))
                .isInstanceOf(CommonsApiException.class);
        assertThatThrownBy(() -> client.getCategoryImageInfo("bad\ncategory", 16, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getCategoryImageInfo("Eiffel Tower", 16, "file|ab&x=1"))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void rateLimitAndLargeResponseFailWithUsefulMessages() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://commons.wikimedia.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CommonsApiClient client = new CommonsApiClient(builder.build(), new ObjectMapper());
        server.expect(requestTo(containsString("/w/api.php"))).andRespond(withTooManyRequests());
        server.expect(requestTo(containsString("/w/api.php")))
                .andRespond(withSuccess("x".repeat(512 * 1024 + 1), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getImageInfo(List.of("File:Tower.jpg")))
                .isInstanceOf(CommonsApiException.class).hasMessageContaining("잠시 후");
        assertThatThrownBy(() -> client.getImageInfo(List.of("File:Tower.jpg")))
                .isInstanceOf(CommonsApiException.class).hasMessageContaining("너무 커서");
        server.verify();
    }
}
