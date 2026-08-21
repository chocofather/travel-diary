package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoTourAutofillResponse;
import com.example.travlediary.dto.kto.KtoTourSearchResponse;
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

class KtoTourServiceTest {

    @Test
    void searchesKorService2AndReturnsOnlySupportedDestinationCandidates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoTourService service = service(builder, "sample-key");
        String json = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                  "numOfRows":10,"pageNo":1,"totalCount":3,"items":{"item":[
                    {"contentid":"126508","contenttypeid":"12","title":"창덕궁","addr1":"서울 종로구 율곡로 99",
                     "addr2":"","mapx":"126.991","mapy":"37.579"},
                    {"contentid":"culture-1","contenttypeid":"14","title":"창덕궁 문화관","addr1":"서울 종로구",
                     "mapx":"126.992","mapy":"37.580"},
                    {"contentid":"food-1","contenttypeid":"39","title":"창덕궁 식당","addr1":"서울 종로구"}
                  ]}}}}
                """;

        server.expect(request -> assertSearchRequest(request.getURI(), "창덕궁", "sample-key"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        KtoTourSearchResponse response = service.search("창덕궁", 1, 10);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.numOfRows()).isEqualTo(10);
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.items()).extracting("contentId").containsExactly("126508", "culture-1");
        assertThat(response.items().get(0)).satisfies(item -> {
            assertThat(item.contentTypeId()).isEqualTo("12");
            assertThat(item.contentTypeName()).isEqualTo("관광지");
            assertThat(item.title()).isEqualTo("창덕궁");
            assertThat(item.address()).isEqualTo("서울 종로구 율곡로 99");
            assertThat(item.longitude()).isEqualTo("126.991");
            assertThat(item.latitude()).isEqualTo("37.579");
        });
        server.verify();
    }

    @Test
    void keepsEncodedGeneralKeyUnchangedInTheFinalRawQuery() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String encodedKey = "synthetic%2Bkey%2Fwith%3Dreserved";
        KtoTourService service = service(builder, encodedKey);

        server.expect(request -> assertSearchRequest(request.getURI(), "창덕궁", encodedKey))
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

        assertThat(service.search("창덕궁", 1, 10).items()).isEmpty();
        server.verify();
    }

    @Test
    void handlesEmptySearchItemsWithoutFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoTourService service = service(builder, "sample-key");
        server.expect(request -> { }).andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

        assertThat(service.search("없는 장소", 1, 10).items()).isEmpty();
        server.verify();
    }

    @Test
    void mergesCommonAndAttractionIntroAndSanitizesHtml() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoTourService service = service(builder, "sample-key");
        String common = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[{
                  "contentid":"126508","contenttypeid":"12","title":"창덕궁",
                  "overview":"조선의 궁궐<br>두 번째 <b>문장</b><script>alert(1)</script>",
                  "addr1":"서울 종로구 율곡로 99","addr2":"와룡동", "mapx":"126.991", "mapy":"37.579",
                  "homepage":"<a href=\\\"https://royal.khs.go.kr/\\\">공식 홈페이지</a>","tel":"02-3668-2300"
                }]}}}}
                """;
        String intro = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[{
                  "contentid":"126508","contenttypeid":"12",
                  "restdate":"매주 월요일(공휴일인 경우 다음날 휴관), 1월 1일, 설날 및 추석 당일, 기관 사정에 따른 임시 휴관일",
                  "usetime":"3월~10월 09:00~18:00<br>11월~2월 09:00~17:00<br>입장 마감은 관람 종료 1시간 전",
                  "usefee":"개인 성인 3,000원, 청소년 1,500원, 어린이 무료<br>단체 성인 2,400원, 청소년 1,200원",
                  "infocenter":"안내소 02-0000-0000",
                  "expguide":"한국어 정규 해설과 외국어 해설은 운영 시간이 다르며 사전 예약이 필요할 수 있습니다. 현장 운영 상황에 따라 일부 해설 일정이 변경될 수 있습니다."
                }]}}}}
                """;

        server.expect(request -> assertDetailRequest(request.getURI(), "/detailCommon2", false))
                .andRespond(withSuccess(common, MediaType.APPLICATION_JSON));
        server.expect(request -> assertDetailRequest(request.getURI(), "/detailIntro2", true))
                .andRespond(withSuccess(intro, MediaType.APPLICATION_JSON));

        KtoTourAutofillResponse result = service.getDetail("126508", "12");

        assertThat(result.contentId()).isEqualTo("126508");
        assertThat(result.contentTypeId()).isEqualTo("12");
        assertThat(result.title()).isEqualTo("창덕궁");
        assertThat(result.address()).isEqualTo("서울 종로구 율곡로 99 와룡동");
        assertThat(result.longitude()).isEqualTo("126.991");
        assertThat(result.latitude()).isEqualTo("37.579");
        assertThat(result.overview()).isEqualTo("조선의 궁궐\n두 번째 문장");
        assertThat(result.homepageUrl()).isEqualTo("https://royal.khs.go.kr/");
        assertThat(result.contactNumber()).isEqualTo("02-3668-2300");
        assertThat(result.closedDays()).isEqualTo(
                "매주 월요일(공휴일인 경우 다음날 휴관), 1월 1일, 설날 및 추석 당일, 기관 사정에 따른 임시 휴관일");
        assertThat(result.openingHours()).isEqualTo(
                "3월~10월 09:00~18:00\n11월~2월 09:00~17:00\n입장 마감은 관람 종료 1시간 전");
        assertThat(result.admissionFee()).isEqualTo(
                "개인 성인 3,000원, 청소년 1,500원, 어린이 무료\n단체 성인 2,400원, 청소년 1,200원");
        assertThat(result.guide()).isEqualTo(
                "한국어 정규 해설과 외국어 해설은 운영 시간이 다르며 사전 예약이 필요할 수 있습니다. 현장 운영 상황에 따라 일부 해설 일정이 변경될 수 있습니다.");
        server.verify();
    }

    @Test
    void normalizesContentTypeSpecificIntroFieldsAndToleratesMissingValues() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoTourService service = service(builder, "sample-key");
        String common = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[{
                  "contentid":"culture-1","contenttypeid":"14","title":"문화시설","homepage":"javascript:alert(1)"
                }]}}}}
                """;
        String intro = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[{
                  "restdateculture":"화요일","usetimeculture":"10:00~17:00","usefee":"무료",
                  "infocenterculture":"02-1111-2222"
                }]}}}}
                """;
        server.expect(request -> { }).andRespond(withSuccess(common, MediaType.APPLICATION_JSON));
        server.expect(request -> { }).andRespond(withSuccess(intro, MediaType.APPLICATION_JSON));

        KtoTourAutofillResponse result = service.getDetail("culture-1", "14");

        assertThat(result.address()).isNull();
        assertThat(result.longitude()).isNull();
        assertThat(result.latitude()).isNull();
        assertThat(result.overview()).isNull();
        assertThat(result.homepageUrl()).isNull();
        assertThat(result.closedDays()).isEqualTo("화요일");
        assertThat(result.openingHours()).isEqualTo("10:00~17:00");
        assertThat(result.admissionFee()).isEqualTo("무료");
        assertThat(result.contactNumber()).isEqualTo("02-1111-2222");
        server.verify();
    }

    @Test
    void rejectsMissingKeyBeforeAnyHttpCallAndUsesSafeFailures() {
        KtoTourService missingKeyService = service(RestClient.builder(), "  ");
        assertThatThrownBy(() -> missingKeyService.search("창덕궁", 1, 10))
                .isInstanceOf(KtoTourApiException.class)
                .hasMessage("TourAPI 인증키가 설정되지 않았습니다.");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String key = "synthetic%2Bsecret%3D";
        KtoTourService service = service(builder, key);
        server.expect(request -> { }).andRespond(withServerError());

        assertThatThrownBy(() -> service.search("창덕궁", 1, 10))
                .isInstanceOf(KtoTourApiException.class)
                .hasMessage("관광정보를 불러오지 못했습니다.")
                .hasMessageNotContaining(key);
        server.verify();
    }

    private KtoTourService service(RestClient.Builder builder, String apiKey) {
        return new KtoTourService(builder, new ObjectMapper(), apiKey,
                "https://kto.example.test/KorService2");
    }

    private void assertSearchRequest(URI uri, String keyword, String encodedKey) {
        assertThat(uri.getPath()).isEqualTo("/KorService2/searchKeyword2");
        assertDecodedQuery(uri, "keyword", keyword);
        assertDecodedQuery(uri, "MobileOS", "ETC");
        assertDecodedQuery(uri, "MobileApp", "TravelDiary");
        assertDecodedQuery(uri, "_type", "json");
        assertDecodedQuery(uri, "pageNo", "1");
        assertDecodedQuery(uri, "numOfRows", "10");
        assertThat(rawQueryValue(uri, "serviceKey")).isEqualTo(encodedKey);
    }

    private void assertDetailRequest(URI uri, String expectedPath, boolean intro) {
        assertThat(uri.getPath()).isEqualTo("/KorService2" + expectedPath);
        assertDecodedQuery(uri, "contentId", "126508");
        if (intro) {
            assertDecodedQuery(uri, "contentTypeId", "12");
        }
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
                  "numOfRows":10,"pageNo":1,"totalCount":0,"items":""}}}
                """;
    }
}
