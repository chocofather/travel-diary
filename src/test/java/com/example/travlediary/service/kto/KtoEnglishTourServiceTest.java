package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoEnglishTourDetailResponse;
import com.example.travlediary.dto.kto.KtoEnglishTourMatchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KtoEnglishTourServiceTest {

    @Test
    void returnsNoMatchWhenLocationHasNoEnglishCandidates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoEnglishTourService service = service(builder, "sample-key");

        server.expect(request -> assertLocationRequest(request.getURI(), "126.991", "37.579", "sample-key"))
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

        KtoEnglishTourMatchResponse result = service.match("경복궁", "126.991", "37.579");

        assertThat(result.status()).isEqualTo(KtoEnglishTourMatchResponse.Status.NO_MATCH);
        assertThat(result.matched()).isNull();
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }

    @Test
    void automaticallyMatchesTheOnlyCandidateWhoseTrailingKoreanAliasExactlyMatches() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoEnglishTourService service = service(builder, "sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                [{"contentid":"gate","contenttypeid":"76","title":"Gwanghwamun Gate (광화문)",
                  "mapx":"126.0","mapy":"37.000018","dist":"2"},
                 {"contentid":"eng-1","contenttypeid":"76","title":"Changdeokgung Palace (창덕궁)",
                  "mapx":"126.0","mapy":"37.000315","dist":"35"}]
                """), MediaType.APPLICATION_JSON));

        KtoEnglishTourMatchResponse result = service.match(" 창덕궁 ", "126.0", "37.0");

        assertThat(result.status()).isEqualTo(KtoEnglishTourMatchResponse.Status.MATCHED);
        assertThat(result.matched().contentId()).isEqualTo("eng-1");
        assertThat(result.matched().title()).isEqualTo("Changdeokgung Palace (창덕궁)");
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }

    @Test
    void doesNotAutomaticallyMatchASoleNearbyCandidateWithoutAKoreanAlias() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoEnglishTourService service = service(builder, "sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"nearby","contenttypeid":"76","title":"Gyeongbokgung Palace",
                 "mapx":"126.99101","mapy":"37.57901","dist":"2"}
                """), MediaType.APPLICATION_JSON));

        KtoEnglishTourMatchResponse result = service.match("경복궁", "126.991", "37.579");

        assertThat(result.status()).isEqualTo(KtoEnglishTourMatchResponse.Status.NO_MATCH);
        assertThat(result.matched()).isNull();
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }

    @Test
    void doesNotMatchAnyNearbyPlaceWhenGyeongbokgungAliasIsAbsent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoEnglishTourService service = service(builder, "sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                [{"contentid":"gwanghwamun","contenttypeid":"76","title":"Gwanghwamun Gate (광화문)",
                  "mapx":"126.0","mapy":"37.000072","dist":"8"},
                 {"contentid":"park","contenttypeid":"78","title":"Sejong-ro Park (세종로공원)",
                  "mapx":"126.0","mapy":"37.000315","dist":"35"},
                 {"contentid":"museum","contenttypeid":"78","title":"National Palace Museum of Korea (국립고궁박물관)",
                  "mapx":"126.0","mapy":"37.001800","dist":"200"}]
                """), MediaType.APPLICATION_JSON));

        KtoEnglishTourMatchResponse result = service.match("경복궁", "126.0", "37.0");

        assertThat(result.status()).isEqualTo(KtoEnglishTourMatchResponse.Status.NO_MATCH);
        assertThat(result.matched()).isNull();
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }

    @Test
    void exactAliasWinsWithoutUsingNearestCandidateDistance() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoEnglishTourService service = service(builder, "sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                [{"contentid":"palace","contenttypeid":"76","title":"Changdeokgung Palace (창덕궁)",
                  "mapx":"126.0","mapy":"37.000720","dist":"80"},
                 {"contentid":"near","contenttypeid":"76","title":"Nearby Gate (돈화문)",
                  "mapx":"126.0","mapy":"37.000018","dist":"2"}]
                """), MediaType.APPLICATION_JSON));

        KtoEnglishTourMatchResponse result = service.match("창덕궁", "126.0", "37.0");

        assertThat(result.status()).isEqualTo(KtoEnglishTourMatchResponse.Status.MATCHED);
        assertThat(result.matched().contentId()).isEqualTo("palace");
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }

    @Test
    void returnsNoMatchWhenSeveralCandidatesHaveTheSameExactKoreanAlias() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoEnglishTourService service = service(builder, "sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                [{"contentid":"second","contenttypeid":"78","title":"Changdeokgung Annex (창덕궁)",
                  "mapx":"126.9913","mapy":"37.5793","dist":"42"},
                 {"contentid":"first","contenttypeid":"76","title":"Changdeokgung Palace (창덕궁)",
                  "mapx":"126.9912","mapy":"37.5792","dist":"28"}]
                """), MediaType.APPLICATION_JSON));

        KtoEnglishTourMatchResponse result = service.match("창덕궁", "126.991", "37.579");

        assertThat(result.status()).isEqualTo(KtoEnglishTourMatchResponse.Status.NO_MATCH);
        assertThat(result.matched()).isNull();
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }

    @Test
    void malformedCoordinatesDoNotHideADuplicateExactAlias() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoEnglishTourService service = service(builder, "sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                [{"contentid":"valid","contenttypeid":"76","title":"Changdeokgung Palace (창덕궁)",
                  "mapx":"126.9912","mapy":"37.5792"},
                 {"contentid":"malformed","contenttypeid":"76","title":"Changdeokgung Annex (창덕궁)",
                  "mapx":"not-a-coordinate","mapy":"37.5793"}]
                """), MediaType.APPLICATION_JSON));

        KtoEnglishTourMatchResponse result = service.match("창덕궁", "126.991", "37.579");

        assertThat(result.status()).isEqualTo(KtoEnglishTourMatchResponse.Status.NO_MATCH);
        assertThat(result.matched()).isNull();
        server.verify();
    }

    @Test
    void extractsOnlyATrailingKoreanAliasAndPreservesNormalEnglishParentheses() {
        assertThat(KtoEnglishTitleMatcher.extractKoreanAlias("Gwanghwamun Gate (광화문)"))
                .isEqualTo("광화문");
        assertThat(KtoEnglishTitleMatcher.extractKoreanAlias("Changdeokgung Palace (  창덕궁  )"))
                .isEqualTo("창덕궁");
        assertThat(KtoEnglishTitleMatcher.extractKoreanAlias("Gyeongbokgung Palace")).isNull();
        assertThat(KtoEnglishTitleMatcher.extractKoreanAlias("Museum (Main Hall)")).isNull();
        assertThat(KtoEnglishTitleMatcher.extractKoreanAlias("Museum (Seoul 서울 Branch)")).isNull();
        assertThat(KtoEnglishTitleMatcher.stripTrailingKoreanAlias("Changdeokgung Palace (창덕궁)"))
                .isEqualTo("Changdeokgung Palace");
        assertThat(KtoEnglishTitleMatcher.stripTrailingKoreanAlias("Museum (Main Hall)"))
                .isEqualTo("Museum (Main Hall)");
        assertThat(KtoEnglishTitleMatcher.stripTrailingKoreanAlias("Museum (Seoul 서울 Branch)"))
                .isEqualTo("Museum (Seoul 서울 Branch)");
    }

    @Test
    void loadsEnglishDetailAndConvertsOverviewHtmlToPlainText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoEnglishTourService service = service(builder, "sample-key");
        String json = responseWithItems("""
                {"contentid":"gyeongbokgung","title":" Gyeongbokgung Palace (경복궁) ",
                 "overview":"Royal palace<br>Second &amp; line <b>text</b><script>bad()</script>"}
                """);
        server.expect(request -> assertDetailRequest(request.getURI(), "gyeongbokgung"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        KtoEnglishTourDetailResponse result = service.getDetail("gyeongbokgung");

        assertThat(result.title()).isEqualTo("Gyeongbokgung Palace");
        assertThat(result.overview()).isEqualTo("Royal palace\nSecond & line text");
        server.verify();
    }

    @Test
    void toleratesBlankEnglishDetailValues() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoEnglishTourService service = service(builder, "sample-key");
        server.expect(request -> { }).andRespond(withSuccess(
                responseWithItems("{\"contentid\":\"eng-1\",\"title\":\" \",\"overview\":null}"),
                MediaType.APPLICATION_JSON));

        KtoEnglishTourDetailResponse result = service.getDetail("eng-1");

        assertThat(result.title()).isNull();
        assertThat(result.overview()).isNull();
        server.verify();
    }

    @Test
    void keepsEncodedEnglishKeyUnchangedAndUsesSafeFailures() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String encodedKey = "synthetic%2Benglish%2Fkey%3D";
        KtoEnglishTourService service = service(builder, encodedKey);
        server.expect(request -> assertLocationRequest(request.getURI(), "126.991", "37.579", encodedKey))
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

        assertThat(service.match("경복궁", "126.991", "37.579").status())
                .isEqualTo(KtoEnglishTourMatchResponse.Status.NO_MATCH);
        server.verify();

        KtoEnglishTourService missing = service(RestClient.builder(), "  ");
        assertThatThrownBy(() -> missing.match("경복궁", "126.991", "37.579"))
                .isInstanceOf(KtoEnglishTourApiException.class)
                .hasMessage("영문 TourAPI 인증키가 설정되지 않았습니다.");

        RestClient.Builder failingBuilder = RestClient.builder();
        MockRestServiceServer failingServer = MockRestServiceServer.bindTo(failingBuilder).build();
        KtoEnglishTourService failing = service(failingBuilder, encodedKey);
        failingServer.expect(request -> { }).andRespond(withServerError());
        assertThatThrownBy(() -> failing.match("경복궁", "126.991", "37.579"))
                .isInstanceOf(KtoEnglishTourApiException.class)
                .hasMessage("영문 관광정보를 불러오지 못했습니다.")
                .hasMessageNotContaining(encodedKey);
        failingServer.verify();
    }

    private KtoEnglishTourService service(RestClient.Builder builder, String apiKey) {
        return new KtoEnglishTourService(builder, new ObjectMapper(), apiKey,
                "https://kto.example.test/EngService2");
    }

    private void assertLocationRequest(URI uri, String mapX, String mapY, String encodedKey) {
        assertThat(uri.getPath()).isEqualTo("/EngService2/locationBasedList2");
        assertDecodedQuery(uri, "mapX", mapX);
        assertDecodedQuery(uri, "mapY", mapY);
        assertDecodedQuery(uri, "radius", "500");
        assertDecodedQuery(uri, "arrange", "E");
        assertDecodedQuery(uri, "pageNo", "1");
        assertDecodedQuery(uri, "numOfRows", "20");
        assertDecodedQuery(uri, "MobileOS", "ETC");
        assertDecodedQuery(uri, "MobileApp", "TravelDiary");
        assertDecodedQuery(uri, "_type", "json");
        assertThat(rawQueryValue(uri, "serviceKey")).isEqualTo(encodedKey);
    }

    private void assertDetailRequest(URI uri, String contentId) {
        assertThat(uri.getPath()).isEqualTo("/EngService2/detailCommon2");
        assertDecodedQuery(uri, "contentId", contentId);
    }

    private void assertDecodedQuery(URI uri, String name, String expected) {
        assertThat(UriUtils.decode(rawQueryValue(uri, name), StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    private String rawQueryValue(URI uri, String name) {
        String prefix = name + "=";
        return Arrays.stream(uri.getRawQuery().split("&"))
                .filter(part -> part.startsWith(prefix))
                .map(part -> part.substring(prefix.length()))
                .findFirst()
                .orElse(null);
    }

    private String emptyResponse() {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                  "numOfRows":20,"pageNo":1,"totalCount":0,"items":""}}}
                """;
    }

    private String responseWithItems(String items) {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                  "numOfRows":20,"pageNo":1,"totalCount":1,"items":{"item":%s}}}}
                """.formatted(items);
    }
}
