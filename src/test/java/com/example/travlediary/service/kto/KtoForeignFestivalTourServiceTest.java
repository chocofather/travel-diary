package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoForeignTourDetailResponse;
import com.example.travlediary.dto.kto.KtoForeignTourMatchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 외국어 축제·행사(contentTypeId=85) 자동입력.
 *
 * <p>실제 Eng/Jpn/Chs/Cht Service2 응답을 확인해 만든 계약이다.
 * detailIntro2 는 네 언어가 같은 필드 이름(eventplace / playtime / usetimefestival /
 * sponsor1 / sponsor2)을 쓰고, 주소 필드는 없어 detailCommon2 의 addr1·addr2 를 쓴다.
 * 외국어 locationBasedList2 는 85 를 돌려주지 않아 국문 제목 키워드 검색으로 따로 찾는다.
 */
class KtoForeignFestivalTourServiceTest {

    @Test
    void festivalIsFoundByKoreanTitleBecauseTheLocationListNeverReturnsIt() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        // 좌표 목록에는 축제가 없다 (실측 확인).
        server.expect(request -> assertPath(request.getURI(), "/EngService2/locationBasedList2"))
                .andRespond(withSuccess(responseWithItems("""
                        {"contentid":"eng-76","contenttypeid":"76","title":"Some Museum (박물관)",
                         "mapx":"127.0259140093","mapy":"37.6417208880"}
                        """), MediaType.APPLICATION_JSON));
        server.expect(request -> {
            assertPath(request.getURI(), "/EngService2/searchKeyword2");
            assertDecodedQuery(request.getURI(), "keyword", "백년나이트 야시장");
        }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"3544280","contenttypeid":"85",
                 "title":"100 Years Night Night Market (백년나이트 야시장)",
                 "mapx":"127.0259140093","mapy":"37.6417208880"}
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,
                "백년나이트 야시장", "127.0259140093", "37.6417208880", true);

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.MATCHED);
        assertThat(result.matched().contentId()).isEqualTo("3544280");
        assertThat(result.matched().contentTypeId()).isEqualTo("85");
        server.verify();
    }

    /**
     * 국문 이름 자체가 괄호를 품는 축제. 외국어 제목은 괄호가 겹쳐 온다.
     *
     * <p>실측: 국문 3537267 "백년나이트 야시장 (메기의 귀환)" ↔
     * 영문 3544280 "100 Years Night Night Market (백년나이트 야시장 (메기의 귀환))".
     * 예전에는 끝 괄호 안에 또 괄호가 있으면 별칭을 못 읽어 후보가 통째로 걸러졌다.
     */
    @Test
    void aFestivalWhoseKoreanNameContainsParenthesesStillMatches() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        server.expect(request -> assertPath(request.getURI(), "/EngService2/locationBasedList2"))
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));
        server.expect(request -> {
            assertPath(request.getURI(), "/EngService2/searchKeyword2");
            // 검색어는 화면의 국문 제목 그대로다.
            assertDecodedQuery(request.getURI(), "keyword", "백년나이트 야시장 (메기의 귀환)");
        }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"3544280","contenttypeid":"85",
                 "title":"100 Years Night Night Market (백년나이트 야시장 (메기의 귀환))",
                 "mapx":"127.0259140093","mapy":"37.6417208880"}
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,
                "백년나이트 야시장 (메기의 귀환)", "127.0259140093", "37.6417208880", true);

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.MATCHED);
        assertThat(result.matched().contentId()).isEqualTo("3544280");
        server.verify();
    }

    @Test
    void aNestedAliasThatDoesNotMatchTheKoreanNameIsStillRejected() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        server.expect(request -> { })
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));
        // 괄호를 함께 읽더라도 국문 이름과 정확히 같아야 한다. 다른 축제는 걸러진다.
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"other","contenttypeid":"85",
                 "title":"Other Night Market (다른 야시장 (봄 축제))",
                 "mapx":"127.0259140093","mapy":"37.6417208880"}
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,
                "백년나이트 야시장 (메기의 귀환)", "127.0259140093", "37.6417208880", true);

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
        server.verify();
    }

    @Test
    void twoFestivalsSharingTheSameKoreanNameStayAmbiguous() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        server.expect(request -> { })
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                [{"contentid":"one","contenttypeid":"85",
                  "title":"First Market (백년나이트 야시장 (메기의 귀환))",
                  "mapx":"127.0259140093","mapy":"37.6417208880"},
                 {"contentid":"two","contenttypeid":"85",
                  "title":"Second Market (백년나이트 야시장 (메기의 귀환))",
                  "mapx":"127.0259140093","mapy":"37.6417208880"}]
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,
                "백년나이트 야시장 (메기의 귀환)", "127.0259140093", "37.6417208880", true);

        // 후보가 둘이면 고르지 않는다.
        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
        server.verify();
    }

    @Test
    void theTranslatedTitleDropsTheWholeNestedKoreanAlias() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"3544280","contenttypeid":"85",
                 "title":"100 Years Night Night Market (백년나이트 야시장 (메기의 귀환))",
                 "addr1":"16 Hancheon-ro 144-gil, Gangbuk-gu, Seoul"}
                """), MediaType.APPLICATION_JSON));
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"3544280","contenttypeid":"85",
                 "eventplace":"Gangbuk-gu 100 Years Market"}
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourDetailResponse result = service.getDetail(
                KtoForeignLanguage.ENGLISH, "3544280", "85");

        assertThat(result.title()).isEqualTo("100 Years Night Night Market");
        server.verify();
    }

    @Test
    void destinationScreensNeverPayForTheExtraFestivalLookup() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        // festival 플래그가 꺼져 있으면 좌표 조회 한 번으로 끝난다 (기존 동작 그대로).
        server.expect(request -> assertPath(request.getURI(), "/EngService2/locationBasedList2"))
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,
                "백년나이트 야시장", "127.0259140093", "37.6417208880");

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
        server.verify();
    }

    @Test
    void aFarAwayFestivalWithTheSameNameIsNotMatched() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        server.expect(request -> { })
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));
        // 키워드 검색은 전국을 훑으므로 좌표 반경 밖 후보는 버린다.
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"far","contenttypeid":"85","title":"Some Festival (벚꽃 축제)",
                 "mapx":"129.0","mapy":"35.1"}
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,
                "벚꽃 축제", "126.991", "37.579", true);

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
        server.verify();
    }

    @Test
    void nonFestivalKeywordHitsAreIgnored() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        server.expect(request -> { })
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"eng-76","contenttypeid":"76","title":"Some Museum (벚꽃 축제)",
                 "mapx":"126.991","mapy":"37.579"}
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,
                "벚꽃 축제", "126.991", "37.579", true);

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
        server.verify();
    }

    @Test
    void keywordSearchFailureLeavesTheMatchAsAnOrdinaryNoMatch() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        server.expect(request -> { })
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));
        server.expect(request -> { }).andRespond(withServerError());

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,
                "벚꽃 축제", "126.991", "37.579", true);

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "ENGLISH,EngService2", "JAPANESE,JpnService2",
            "CHINESE_SIMPLIFIED,ChsService2", "CHINESE_TRADITIONAL,ChtService2"
    })
    void everyLanguageReadsTheSameFestivalIntroFieldNames(KtoForeignLanguage language,
                                                          String servicePath) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        server.expect(request -> assertPath(request.getURI(), "/" + servicePath + "/detailCommon2"))
                .andRespond(withSuccess(responseWithItems("""
                        {"contentid":"3544280","contenttypeid":"85",
                         "title":"100 Years Night Night Market (백년나이트 야시장)",
                         "addr1":"16 Hancheon-ro 144-gil, Gangbuk-gu, Seoul","addr2":"",
                         "overview":"A traditional market turns into a night market."}
                        """), MediaType.APPLICATION_JSON));
        server.expect(request -> {
            assertPath(request.getURI(), "/" + servicePath + "/detailIntro2");
            assertDecodedQuery(request.getURI(), "contentTypeId", "85");
        }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"3544280","contenttypeid":"85",
                 "eventplace":"Gangbuk-gu 100 Years Market","playtime":"16:00-21:00",
                 "usetimefestival":"Free","sponsor1":"Ministry of SMEs and Startups",
                 "sponsor2":"100 Years Market Promotion Organization",
                 "sponsor1tel":"+82-2-903-9110","eventhomepage":"https://example.test",
                 "program":"DJ performance","placeinfo":"","agelimit":""}
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourDetailResponse result = service.getDetail(language, "3544280", "85");

        // 제목은 끝의 국문 별칭을 떼고 돌려준다.
        assertThat(result.title()).isEqualTo("100 Years Night Night Market");
        assertThat(result.overview()).isEqualTo("A traditional market turns into a night market.");
        assertThat(result.eventPlace()).isEqualTo("Gangbuk-gu 100 Years Market");
        assertThat(result.address()).isEqualTo("16 Hancheon-ro 144-gil, Gangbuk-gu, Seoul");
        assertThat(result.playTime()).isEqualTo("16:00-21:00");
        assertThat(result.useTime()).isEqualTo("Free");
        assertThat(result.sponsor1()).isEqualTo("Ministry of SMEs and Startups");
        assertThat(result.sponsor2()).isEqualTo("100 Years Market Promotion Organization");
        // 여행지 유형 칸은 축제에서 비어 있다.
        assertThat(result.closedDays()).isNull();
        assertThat(result.openingHours()).isNull();
        assertThat(result.admissionFee()).isNull();
        assertThat(result.mainMenu()).isNull();
        assertThat(result.roomType()).isNull();
        assertThat(result.mainProducts()).isNull();
        server.verify();
    }

    @Test
    void secondAddressLineIsAppendedWhenTheApiSendsOne() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"eng-85","title":"Festival (축제)",
                 "addr1":"161 Sajik-ro, Jongno-gu, Seoul","addr2":"Gyeongbokgung"}
                """), MediaType.APPLICATION_JSON));
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"eng-85","contenttypeid":"85","eventplace":"Gyeongbokgung Palace"}
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourDetailResponse result = service.getDetail(
                KtoForeignLanguage.ENGLISH, "eng-85", "85");

        assertThat(result.address()).isEqualTo("161 Sajik-ro, Jongno-gu, Seoul Gyeongbokgung");
        server.verify();
    }

    @Test
    void missingFestivalIntroFieldsComeBackAsNullRatherThanBlank() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"eng-85","title":"Festival (축제)","addr1":"","addr2":""}
                """), MediaType.APPLICATION_JSON));
        // 언어에 따라 일부 칸이 비어 오는 것은 정상적인 데이터 부족이다.
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"eng-85","contenttypeid":"85","eventplace":"Gyeongbokgung Palace",
                 "playtime":"","usetimefestival":"   ","sponsor1":"","sponsor2":""}
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourDetailResponse result = service.getDetail(
                KtoForeignLanguage.ENGLISH, "eng-85", "85");

        assertThat(result.eventPlace()).isEqualTo("Gyeongbokgung Palace");
        assertThat(result.address()).isNull();
        assertThat(result.playTime()).isNull();
        assertThat(result.useTime()).isNull();
        assertThat(result.sponsor1()).isNull();
        assertThat(result.sponsor2()).isNull();
        server.verify();
    }

    @Test
    void introFailureStillReturnsTitleOverviewAndAddress() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"eng-85","title":"Festival (축제)","overview":"Overview text",
                 "addr1":"161 Sajik-ro, Jongno-gu, Seoul"}
                """), MediaType.APPLICATION_JSON));
        server.expect(request -> { }).andRespond(withServerError());

        KtoForeignTourDetailResponse result = service.getDetail(
                KtoForeignLanguage.ENGLISH, "eng-85", "85");

        assertThat(result.title()).isEqualTo("Festival");
        assertThat(result.overview()).isEqualTo("Overview text");
        // 주소는 detailCommon2 에서 오므로 intro 실패와 무관하다.
        assertThat(result.address()).isEqualTo("161 Sajik-ro, Jongno-gu, Seoul");
        assertThat(result.eventPlace()).isNull();
        assertThat(result.playTime()).isNull();
        server.verify();
    }

    @Test
    void otherContentTypesNeverPickUpFestivalValues() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");

        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"eng-76","title":"Palace (경복궁)","addr1":"161 Sajik-ro"}
                """), MediaType.APPLICATION_JSON));
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"eng-76","contenttypeid":"76","restdate":"Tuesday",
                 "usetime":"09:00-18:00","eventplace":"leaked","sponsor1":"leaked"}
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourDetailResponse result = service.getDetail(
                KtoForeignLanguage.ENGLISH, "eng-76", "76");

        assertThat(result.closedDays()).isEqualTo("Tuesday");
        assertThat(result.openingHours()).isEqualTo("09:00-18:00");
        // 관광지 응답에 축제 필드가 섞여 있어도 축제 칸으로 옮기지 않는다.
        assertThat(result.eventPlace()).isNull();
        assertThat(result.sponsor1()).isNull();
        assertThat(result.address()).isNull();
        server.verify();
    }

    private KtoForeignTourService service(RestClient.Builder builder, String apiKey) {
        return new KtoForeignTourService(builder, new ObjectMapper(), apiKey,
                "https://kto.example.test/EngService2",
                "https://kto.example.test/JpnService2",
                "https://kto.example.test/ChsService2",
                "https://kto.example.test/ChtService2");
    }

    private void assertPath(URI uri, String expected) {
        assertThat(uri.getPath()).isEqualTo(expected);
    }

    private void assertDecodedQuery(URI uri, String name, String expected) {
        assertThat(UriUtils.decode(rawQueryValue(uri, name), StandardCharsets.UTF_8))
                .isEqualTo(expected);
    }

    private String rawQueryValue(URI uri, String name) {
        return Arrays.stream(uri.getRawQuery().split("&"))
                .filter(pair -> pair.startsWith(name + "="))
                .map(pair -> pair.substring(name.length() + 1))
                .findFirst()
                .orElseThrow();
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
