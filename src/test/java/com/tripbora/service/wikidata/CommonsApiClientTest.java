package com.tripbora.service.wikidata;

import com.fasterxml.jackson.databind.JsonNode;
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
                // generator 에 tltemplates 를 걸면 Commons가 20초 넘게 걸릴 수 있어 파일 정보만 받는다.
                .andExpect(queryParam("prop", "imageinfo"))
                .andExpect(request -> assertThat(request.getURI().toString()).doesNotContain("tltemplates"))
                .andExpect(queryParam("iiurlwidth", "240"))
                // 미리보기는 판별에 쓰는 extmetadata 항목만 받는다.
                .andExpect(requestTo(containsString("iiextmetadatafilter=Artist")))
                .andRespond(withSuccess("{\"query\":{\"categorymembers\":[{\"ns\":6,\"title\":\"File:A.jpg\"}],"
                        + "\"pages\":[{\"pageid\":11,\"title\":\"File:A.jpg\",\"imageinfo\":[{}]}]}}", MediaType.APPLICATION_JSON));
        // 라이선스 템플릿은 묶음 전체를 pageids 한 요청으로 받는다.
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("prop", "templates"))
                .andExpect(queryParam("pageids", "11"))
                .andExpect(queryParam("tlnamespace", "10"))
                .andRespond(withSuccess("{\"query\":{\"pages\":[{\"pageid\":11,\"templates\":["
                        + "{\"ns\":10,\"title\":\"Template:Cc-by-sa-4.0\"},{\"ns\":10,\"title\":\"Template:Information\"}]}]}}",
                        MediaType.APPLICATION_JSON));
        // 다음 묶음은 목록과 파일 정보를 같은 위치에서 이어 받는다.
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("cmcontinue", "file%7Cab%7C1"))
                .andExpect(queryParam("gcmcontinue", "file%7Cab%7C1"))
                .andRespond(withSuccess("{\"query\":{}}", MediaType.APPLICATION_JSON));

        JsonNode pages = client.getCategoryImageInfo("Eiffel Tower", 8, null).path("query").path("pages");
        assertThat(pages).hasSize(1);
        // 판별에 쓰는 템플릿만 남긴다(tltemplates 로 받던 값과 같다).
        assertThat(pages.get(0).path("templates")).hasSize(1);
        assertThat(pages.get(0).path("templates").get(0).path("title").asText()).isEqualTo("Template:Cc-by-sa-4.0");
        assertThatThrownBy(() -> client.getCategoryImageInfo("Eiffel Tower", 8, "file|ab|1"))
                .isInstanceOf(CommonsApiException.class);
        assertThatThrownBy(() -> client.getCategoryImageInfo("bad\ncategory", 16, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getCategoryImageInfo("Eiffel Tower", 16, "file|ab&x=1"))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    /** 수동 검색은 File 네임스페이스의 비트맵만 공식 generator=search 로 찾고, 이어받기는 gsroffset 을 쓴다. */
    @Test
    void searchUsesTheFileNamespaceBitmapFilterAndOfficialOffset() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://commons.wikimedia.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CommonsApiClient client = new CommonsApiClient(builder.build(), new ObjectMapper());
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("generator", "search"))
                .andExpect(queryParam("gsrsearch", "Petronas%20Towers%20filetype:bitmap"))
                .andExpect(queryParam("gsrnamespace", "6"))
                .andExpect(queryParam("gsrlimit", "8"))
                .andExpect(queryParam("prop", "imageinfo"))
                .andExpect(request -> assertThat(request.getURI().toString()).doesNotContain("tltemplates"))
                .andExpect(requestTo(containsString("iiextmetadatafilter=Artist")))
                .andRespond(withSuccess("{\"batchcomplete\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("gsroffset", "8"))
                .andRespond(withSuccess("{\"query\":{\"pages\":["
                        + "{\"pageid\":1,\"index\":9,\"title\":\"File:A.jpg\"},{\"pageid\":2,\"index\":10,\"title\":\"File:B.jpg\"}]}}",
                        MediaType.APPLICATION_JSON));
        // 검색 결과 파일 수만큼 따로 묻지 않고 묶음 한 번 + 잘린 만큼만 이어 받는다.
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("prop", "templates"))
                .andExpect(queryParam("pageids", "1%7C2"))
                .andRespond(withSuccess("{\"continue\":{\"tlcontinue\":\"2|10|Information\",\"continue\":\"||\"},"
                        + "\"query\":{\"pages\":[{\"pageid\":1,\"templates\":[{\"ns\":10,\"title\":\"Template:PD-self\"}]},"
                        + "{\"pageid\":2,\"templates\":[{\"ns\":10,\"title\":\"Template:Licensed-PD\"}]}]}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("pageids", "1%7C2"))
                .andExpect(queryParam("tlcontinue", "2%7C10%7CInformation"))
                .andRespond(withSuccess("{\"query\":{\"pages\":[{\"pageid\":1},"
                        + "{\"pageid\":2,\"templates\":[{\"ns\":10,\"title\":\"Template:Cc-by-4.0\"}]}]}}",
                        MediaType.APPLICATION_JSON));

        // 결과가 없으면 Commons 는 query 없이 답한다. 오류가 아니다. 템플릿도 묻지 않는다.
        assertThat(client.searchImageInfo("Petronas Towers", 8, 0).has("query")).isFalse();
        JsonNode pages = client.searchImageInfo("Petronas Towers", 8, 8).path("query").path("pages");
        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).path("index").asInt()).isEqualTo(9);
        assertThat(pages.get(0).path("templates").findValuesAsText("title")).containsExactly("Template:PD-self");
        // 이어 받은 템플릿도 같은 파일에 합친다.
        assertThat(pages.get(1).path("templates").findValuesAsText("title"))
                .containsExactly("Template:Licensed-PD", "Template:Cc-by-4.0");
        assertThatThrownBy(() -> client.searchImageInfo("bad\nquery", 8, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.searchImageInfo("Tower", 8, 10_000))
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
