package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoForeignTourDetailResponse;
import com.example.travlediary.dto.kto.KtoForeignTourMatchResponse;
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

class KtoForeignTourServiceTest {

    @Test
    void returnsNoMatchWhenLocationHasNoEnglishCandidates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder,"sample-key");

        server.expect(request -> assertLocationRequest(request.getURI(), "126.991", "37.579", "sample-key"))
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,"경복궁", "126.991", "37.579");

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
        assertThat(result.matched()).isNull();
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }

    @Test
    void automaticallyMatchesTheOnlyCandidateWhoseTrailingKoreanAliasExactlyMatches() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder,"sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                [{"contentid":"gate","contenttypeid":"76","title":"Gwanghwamun Gate (광화문)",
                  "mapx":"126.0","mapy":"37.000018","dist":"2"},
                 {"contentid":"eng-1","contenttypeid":"76","title":"Changdeokgung Palace (창덕궁)",
                  "mapx":"126.0","mapy":"37.000315","dist":"35"}]
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH," 창덕궁 ", "126.0", "37.0");

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.MATCHED);
        assertThat(result.matched().contentId()).isEqualTo("eng-1");
        assertThat(result.matched().title()).isEqualTo("Changdeokgung Palace (창덕궁)");
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }

    @Test
    void doesNotAutomaticallyMatchASoleNearbyCandidateWithoutAKoreanAlias() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder,"sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"nearby","contenttypeid":"76","title":"Gyeongbokgung Palace",
                 "mapx":"126.99101","mapy":"37.57901","dist":"2"}
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,"경복궁", "126.991", "37.579");

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
        assertThat(result.matched()).isNull();
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }

    @Test
    void doesNotMatchAnyNearbyPlaceWhenGyeongbokgungAliasIsAbsent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder,"sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                [{"contentid":"gwanghwamun","contenttypeid":"76","title":"Gwanghwamun Gate (광화문)",
                  "mapx":"126.0","mapy":"37.000072","dist":"8"},
                 {"contentid":"park","contenttypeid":"78","title":"Sejong-ro Park (세종로공원)",
                  "mapx":"126.0","mapy":"37.000315","dist":"35"},
                 {"contentid":"museum","contenttypeid":"78","title":"National Palace Museum of Korea (국립고궁박물관)",
                  "mapx":"126.0","mapy":"37.001800","dist":"200"}]
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,"경복궁", "126.0", "37.0");

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
        assertThat(result.matched()).isNull();
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }

    @Test
    void exactAliasWinsWithoutUsingNearestCandidateDistance() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder,"sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                [{"contentid":"palace","contenttypeid":"76","title":"Changdeokgung Palace (창덕궁)",
                  "mapx":"126.0","mapy":"37.000720","dist":"80"},
                 {"contentid":"near","contenttypeid":"76","title":"Nearby Gate (돈화문)",
                  "mapx":"126.0","mapy":"37.000018","dist":"2"}]
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,"창덕궁", "126.0", "37.0");

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.MATCHED);
        assertThat(result.matched().contentId()).isEqualTo("palace");
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }

    @Test
    void returnsNoMatchWhenSeveralCandidatesHaveTheSameExactKoreanAlias() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder,"sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                [{"contentid":"second","contenttypeid":"78","title":"Changdeokgung Annex (창덕궁)",
                  "mapx":"126.9913","mapy":"37.5793","dist":"42"},
                 {"contentid":"first","contenttypeid":"76","title":"Changdeokgung Palace (창덕궁)",
                  "mapx":"126.9912","mapy":"37.5792","dist":"28"}]
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,"창덕궁", "126.991", "37.579");

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
        assertThat(result.matched()).isNull();
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }

    @Test
    void malformedCoordinatesDoNotHideADuplicateExactAlias() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder,"sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                [{"contentid":"valid","contenttypeid":"76","title":"Changdeokgung Palace (창덕궁)",
                  "mapx":"126.9912","mapy":"37.5792"},
                 {"contentid":"malformed","contenttypeid":"76","title":"Changdeokgung Annex (창덕궁)",
                  "mapx":"not-a-coordinate","mapy":"37.5793"}]
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(KtoForeignLanguage.ENGLISH,"창덕궁", "126.991", "37.579");

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
        assertThat(result.matched()).isNull();
        server.verify();
    }

    @Test
    void extractsOnlyATrailingKoreanAliasAndPreservesNormalEnglishParentheses() {
        assertThat(KtoKoreanAliasMatcher.extractKoreanAlias("Gwanghwamun Gate (광화문)"))
                .isEqualTo("광화문");
        assertThat(KtoKoreanAliasMatcher.extractKoreanAlias("Changdeokgung Palace (  창덕궁  )"))
                .isEqualTo("창덕궁");
        assertThat(KtoKoreanAliasMatcher.extractKoreanAlias("Gyeongbokgung Palace")).isNull();
        assertThat(KtoKoreanAliasMatcher.extractKoreanAlias("Museum (Main Hall)")).isNull();
        assertThat(KtoKoreanAliasMatcher.extractKoreanAlias("Museum (Seoul 서울 Branch)")).isNull();
        assertThat(KtoKoreanAliasMatcher.stripTrailingKoreanAlias("Changdeokgung Palace (창덕궁)"))
                .isEqualTo("Changdeokgung Palace");
        assertThat(KtoKoreanAliasMatcher.stripTrailingKoreanAlias("Museum (Main Hall)"))
                .isEqualTo("Museum (Main Hall)");
        assertThat(KtoKoreanAliasMatcher.stripTrailingKoreanAlias("Museum (Seoul 서울 Branch)"))
                .isEqualTo("Museum (Seoul 서울 Branch)");
    }

    /**
     * 일본어·중국어 자료는 같은 자리에 전각 괄호를 쓰는 경우가 많다.
     * 반각과 똑같이 다루되, 짝이 맞지 않는 괄호까지 억지로 읽지는 않는다.
     */
    @Test
    void readsTheKoreanAliasFromFullWidthParenthesesTheSameWayAsAsciiOnes() {
        assertThat(KtoKoreanAliasMatcher.extractKoreanAlias("景福宮（경복궁）")).isEqualTo("경복궁");
        assertThat(KtoKoreanAliasMatcher.extractKoreanAlias("景福宮(경복궁)")).isEqualTo("경복궁");
        assertThat(KtoKoreanAliasMatcher.extractKoreanAlias("国立現代美術館 ソウル（국립현대미술관 서울）"))
                .isEqualTo("국립현대미술관 서울");
        // 앞에 다른 괄호가 있어도 끝에 붙은 것만 본다
        assertThat(KtoKoreanAliasMatcher.extractKoreanAlias("Jeong's Family（チョンスファミリー）（정스패밀리）"))
                .isEqualTo("정스패밀리");
        // 한글이 아닌 전각 괄호는 별칭이 아니다
        assertThat(KtoKoreanAliasMatcher.extractKoreanAlias("国立現代美術館（ソウル館）")).isNull();
        // 반각과 전각을 섞은 짝은 별칭으로 보지 않는다
        assertThat(KtoKoreanAliasMatcher.extractKoreanAlias("景福宮（경복궁)")).isNull();

        assertThat(KtoKoreanAliasMatcher.stripTrailingKoreanAlias("景福宮（경복궁）"))
                .isEqualTo("景福宮");
        assertThat(KtoKoreanAliasMatcher.stripTrailingKoreanAlias("国立現代美術館（ソウル館）"))
                .isEqualTo("国立現代美術館（ソウル館）");
    }

    @Test
    void everyLanguageCallsItsOwnServiceWithTheSharedApiKey() {
        for (var testCase : java.util.Map.of(
                KtoForeignLanguage.ENGLISH, "/EngService2/locationBasedList2",
                KtoForeignLanguage.JAPANESE, "/JpnService2/locationBasedList2",
                KtoForeignLanguage.CHINESE_SIMPLIFIED, "/ChsService2/locationBasedList2",
                KtoForeignLanguage.CHINESE_TRADITIONAL, "/ChtService2/locationBasedList2")
                .entrySet()) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            KtoForeignTourService service = service(builder, "shared-key");

            server.expect(request -> {
                assertThat(request.getURI().getPath()).isEqualTo(testCase.getValue());
                // 다섯 서비스가 같은 인증키를 쓴다
                assertThat(rawQueryValue(request.getURI(), "serviceKey")).isEqualTo("shared-key");
            }).andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

            assertThat(service.match(testCase.getKey(), "경복궁", "126.991", "37.579").status())
                    .isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
            server.verify();
        }
    }

    @Test
    void japaneseMatchingUsesTheFullWidthAliasAndKeepsTheMatchedContentTypeId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");
        server.expect(request ->
                assertThat(request.getURI().getPath()).isEqualTo("/JpnService2/locationBasedList2"))
                .andRespond(withSuccess(responseWithItems("""
                        [{"contentid":"jpn-1","contenttypeid":"78","title":"国立現代美術館 ソウル（국립현대미술관 서울）",
                          "mapx":"126.98","mapy":"37.5786","dist":"12"}]
                        """), MediaType.APPLICATION_JSON));

        KtoForeignTourMatchResponse result = service.match(
                KtoForeignLanguage.JAPANESE, "국립현대미술관 서울", "126.98", "37.5786");

        assertThat(result.status()).isEqualTo(KtoForeignTourMatchResponse.Status.MATCHED);
        assertThat(result.matched().contentId()).isEqualTo("jpn-1");
        // 국문 코드(14)로 바꾸지 않고 외국어 응답 값을 그대로 들고 간다
        assertThat(result.matched().contentTypeId()).isEqualTo("78");
        server.verify();

        // 그 값이 detailIntro2 에 그대로 실린다
        RestClient.Builder detailBuilder = RestClient.builder();
        MockRestServiceServer detailServer = MockRestServiceServer.bindTo(detailBuilder).build();
        KtoForeignTourService detailService = service(detailBuilder, "sample-key");
        detailServer.expect(request ->
                assertThat(request.getURI().getPath()).isEqualTo("/JpnService2/detailCommon2"))
                .andRespond(withSuccess(responseWithItems("""
                        [{"title":"国立現代美術館 ソウル（국립현대미술관 서울）","overview":"美術館です。"}]
                        """), MediaType.APPLICATION_JSON));
        detailServer.expect(request -> {
            assertThat(request.getURI().getPath()).isEqualTo("/JpnService2/detailIntro2");
            assertDecodedQuery(request.getURI(), "contentTypeId", "78");
        }).andRespond(withSuccess(responseWithItems("[{}]"), MediaType.APPLICATION_JSON));

        KtoForeignTourDetailResponse detail = detailService.getDetail(
                KtoForeignLanguage.JAPANESE, "jpn-1", "78");

        // 제목의 한글 별칭은 떼고 현지어만 남긴다
        assertThat(detail.title()).isEqualTo("国立現代美術館 ソウル");
        assertThat(detail.overview()).isEqualTo("美術館です。");
        detailServer.verify();
    }

    @Test
    void unsupportedLocalesAreNotSilentlyMappedToAnotherLanguage() {
        assertThat(KtoForeignLanguage.fromLanguageTag("ja"))
                .contains(KtoForeignLanguage.JAPANESE);
        assertThat(KtoForeignLanguage.fromLanguageTag("zh-CN"))
                .contains(KtoForeignLanguage.CHINESE_SIMPLIFIED);
        assertThat(KtoForeignLanguage.fromLanguageTag("zh-TW"))
                .contains(KtoForeignLanguage.CHINESE_TRADITIONAL);
        // 국문은 외국어 서비스가 없고, 나머지도 비슷한 언어로 대신하지 않는다
        assertThat(KtoForeignLanguage.fromLanguageTag("ko")).isEmpty();
        assertThat(KtoForeignLanguage.fromLanguageTag("zh")).isEmpty();
        assertThat(KtoForeignLanguage.fromLanguageTag("en-US")).isEmpty();
        assertThat(KtoForeignLanguage.fromLanguageTag(" ")).isEmpty();
        assertThat(KtoForeignLanguage.fromLanguageTag(null)).isEmpty();
    }

    @Test
    void loadsEnglishDetailAndConvertsOverviewHtmlToPlainText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder,"sample-key");
        String json = responseWithItems("""
                {"contentid":"gyeongbokgung","title":" Gyeongbokgung Palace (경복궁) ",
                 "overview":"Royal palace<br>Second &amp; line <b>text</b><script>bad()</script>"}
                """);
        server.expect(request -> assertDetailRequest(request.getURI(), "gyeongbokgung"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        // 유형 코드가 없으면 detailIntro2 는 부르지 않는다
        KtoForeignTourDetailResponse result = service.getDetail(KtoForeignLanguage.ENGLISH,"gyeongbokgung", null);

        assertThat(result.title()).isEqualTo("Gyeongbokgung Palace");
        assertThat(result.overview()).isEqualTo("Royal palace\nSecond & line text");
        assertThat(result.mainMenu()).isNull();
        assertThat(result.openingHours()).isNull();
        assertThat(result.closedDays()).isNull();
        server.verify();
    }

    @Test
    void toleratesBlankEnglishDetailValues() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder,"sample-key");
        server.expect(request -> { }).andRespond(withSuccess(
                responseWithItems("{\"contentid\":\"eng-1\",\"title\":\" \",\"overview\":null}"),
                MediaType.APPLICATION_JSON));

        KtoForeignTourDetailResponse result = service.getDetail(KtoForeignLanguage.ENGLISH,"eng-1", "  ");

        assertThat(result.title()).isNull();
        assertThat(result.overview()).isNull();
        server.verify();
    }

    @Test
    void keepsEncodedEnglishKeyUnchangedAndUsesSafeFailures() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String encodedKey = "synthetic%2Benglish%2Fkey%3D";
        KtoForeignTourService service = service(builder,encodedKey);
        server.expect(request -> assertLocationRequest(request.getURI(), "126.991", "37.579", encodedKey))
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

        assertThat(service.match(KtoForeignLanguage.ENGLISH,"경복궁", "126.991", "37.579").status())
                .isEqualTo(KtoForeignTourMatchResponse.Status.NO_MATCH);
        server.verify();

        KtoForeignTourService missing = service(RestClient.builder(), "  ");
        assertThatThrownBy(() -> missing.match(KtoForeignLanguage.ENGLISH, "경복궁", "126.991", "37.579"))
                .isInstanceOf(KtoForeignTourApiException.class)
                .hasMessage("영문 TourAPI 인증키가 설정되지 않았습니다.");

        RestClient.Builder failingBuilder = RestClient.builder();
        MockRestServiceServer failingServer = MockRestServiceServer.bindTo(failingBuilder).build();
        KtoForeignTourService failing = service(failingBuilder, encodedKey);
        failingServer.expect(request -> { }).andRespond(withServerError());
        assertThatThrownBy(() -> failing.match(KtoForeignLanguage.ENGLISH, "경복궁", "126.991", "37.579"))
                .isInstanceOf(KtoForeignTourApiException.class)
                .hasMessage("영문 관광정보를 불러오지 못했습니다.")
                .hasMessageNotContaining(encodedKey);
        failingServer.verify();
    }

    @Test
    void loadsRestaurantIntroFieldsWithTheMatchedEnglishContentTypeId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder,"sample-key");
        server.expect(request -> assertDetailRequest(request.getURI(), "eng-82"))
                .andRespond(withSuccess(responseWithItems("""
                        {"contentid":"eng-82","title":"Daepunggwan (대풍관)","overview":"Seafood"}
                        """), MediaType.APPLICATION_JSON));
        // 영문 유형 코드(82)를 그대로 쓴다. 국문 39 로 바꾸지 않는다.
        server.expect(request -> assertIntroRequest(request.getURI(), "eng-82", "82"))
                .andRespond(withSuccess(responseWithItems("""
                        {"contentid":"eng-82","contenttypeid":"82","firstmenu":"Oyster",
                         "treatmenu":"Seokhwajjim","opentimefood":"Weekdays 11:30-18:00 <br> * Last order",
                         "restdatefood":"Tuesday-Wednesday","infocenterfood":"+82-55-644-4446"}
                        """), MediaType.APPLICATION_JSON));

        KtoForeignTourDetailResponse result = service.getDetail(KtoForeignLanguage.ENGLISH,"eng-82", "82");

        assertThat(result.title()).isEqualTo("Daepunggwan");
        assertThat(result.overview()).isEqualTo("Seafood");
        assertThat(result.mainMenu()).isEqualTo("Oyster");
        assertThat(result.openingHours()).isEqualTo("Weekdays 11:30-18:00\n* Last order");
        assertThat(result.closedDays()).isEqualTo("Tuesday-Wednesday");
        server.verify();
    }

    /**
     * 유형별 상세는 유형마다 필드 이름이 다르다. 매칭 결과가 알려 준 외국어 유형 코드로만 고르며,
     * 대응이 분명한 칸만 채우고 나머지는 비운다. 네 언어가 같은 규칙을 쓴다.
     */
    @Test
    void mapsEverySubtypeIntoTheRightTranslationFieldsInEveryLanguage() {
        for (KtoForeignLanguage language : KtoForeignLanguage.values()) {
            // 관광지(76): restdate / usetime / usefee
            KtoForeignTourDetailResponse attraction = detailOf(language, "76", """
                    {"contenttypeid":"76","restdate":"Monday","usetime":"09:00-18:00",
                     "usefee":"Adults 3,000","usetimeculture":"NOT USED","expguide":"NOT USED"}
                    """);
            assertThat(attraction.closedDays()).as("%s 76", language).isEqualTo("Monday");
            assertThat(attraction.openingHours()).as("%s 76", language).isEqualTo("09:00-18:00");
            assertThat(attraction.admissionFee()).as("%s 76", language).isEqualTo("Adults 3,000");
            assertThat(attraction.mainMenu()).isNull();
            assertThat(attraction.roomType()).isNull();
            assertThat(attraction.mainProducts()).isNull();

            // 문화시설(78): 같은 뜻이지만 필드 이름이 다르다
            KtoForeignTourDetailResponse culture = detailOf(language, "78", """
                    {"contenttypeid":"78","restdateculture":"Tuesday",
                     "usetimeculture":"10:00-20:00","usefee":"Free",
                     "restdate":"NOT USED","usetime":"NOT USED"}
                    """);
            assertThat(culture.closedDays()).as("%s 78", language).isEqualTo("Tuesday");
            assertThat(culture.openingHours()).as("%s 78", language).isEqualTo("10:00-20:00");
            assertThat(culture.admissionFee()).as("%s 78", language).isEqualTo("Free");

            // 음식점·카페(82)
            KtoForeignTourDetailResponse restaurant = detailOf(language, "82", """
                    {"contenttypeid":"82","firstmenu":"Bibimbap","opentimefood":"11:00-21:00",
                     "restdatefood":"Sunday","parkingfood":"NOT USED","infocenterfood":"NOT USED"}
                    """);
            assertThat(restaurant.mainMenu()).as("%s 82", language).isEqualTo("Bibimbap");
            assertThat(restaurant.openingHours()).as("%s 82", language).isEqualTo("11:00-21:00");
            assertThat(restaurant.closedDays()).as("%s 82", language).isEqualTo("Sunday");
            assertThat(restaurant.admissionFee()).isNull();
            assertThat(restaurant.roomType()).isNull();
            assertThat(restaurant.mainProducts()).isNull();

            // 숙박(80): 객실 유형만
            KtoForeignTourDetailResponse lodging = detailOf(language, "80", """
                    {"contenttypeid":"80","roomtype":"Twin / Double","subfacility":"NOT USED",
                     "checkintime":"15:00","checkouttime":"11:00","roomcount":"14"}
                    """);
            assertThat(lodging.roomType()).as("%s 80", language).isEqualTo("Twin / Double");
            assertThat(lodging.closedDays()).isNull();
            assertThat(lodging.openingHours()).isNull();
            assertThat(lodging.admissionFee()).isNull();
            assertThat(lodging.mainMenu()).isNull();
            assertThat(lodging.mainProducts()).isNull();

            // 체험·액티비티(75): 운영시간과 이용요금만
            KtoForeignTourDetailResponse leports = detailOf(language, "75", """
                    {"contenttypeid":"75","usetimeleports":"14:00-22:00",
                     "usefeeleports":"20,000","restdateleports":"NOT USED"}
                    """);
            assertThat(leports.openingHours()).as("%s 75", language).isEqualTo("14:00-22:00");
            assertThat(leports.admissionFee()).as("%s 75", language).isEqualTo("20,000");
            assertThat(leports.closedDays()).isNull();
            assertThat(leports.mainMenu()).isNull();
            assertThat(leports.roomType()).isNull();
            assertThat(leports.mainProducts()).isNull();

            // 쇼핑(79)
            KtoForeignTourDetailResponse shopping = detailOf(language, "79", """
                    {"contenttypeid":"79","restdateshopping":"1st Monday","opentime":"11:00-20:00",
                     "saleitem":"Clothing, food","parkingshopping":"NOT USED"}
                    """);
            assertThat(shopping.closedDays()).as("%s 79", language).isEqualTo("1st Monday");
            assertThat(shopping.openingHours()).as("%s 79", language).isEqualTo("11:00-20:00");
            assertThat(shopping.mainProducts()).as("%s 79", language).isEqualTo("Clothing, food");
            assertThat(shopping.admissionFee()).isNull();
            assertThat(shopping.mainMenu()).isNull();
            assertThat(shopping.roomType()).isNull();
        }
    }

    @Test
    void subtypeValuesAreCleanedUpLikeEveryOtherText() {
        KtoForeignTourDetailResponse detail = detailOf(KtoForeignLanguage.JAPANESE, "79", """
                {"contenttypeid":"79","opentime":"11:00-20:00<br> カフェ 12:00～22:00",
                 "saleitem":"  ","restdateshopping":null}
                """);

        assertThat(detail.openingHours()).isEqualTo("11:00-20:00\nカフェ 12:00～22:00");
        // 공백뿐이거나 없는 값은 빈 칸으로 둔다
        assertThat(detail.mainProducts()).isNull();
        assertThat(detail.closedDays()).isNull();
    }

    /** detailCommon2 + detailIntro2 한 쌍을 흉내 내고 상세를 돌려준다. */
    private KtoForeignTourDetailResponse detailOf(KtoForeignLanguage language,
                                                  String contentTypeId, String introJson) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder, "sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"foreign-1","title":"Place (장소)","overview":"overview"}
                """), MediaType.APPLICATION_JSON));
        server.expect(request -> assertDecodedQuery(
                request.getURI(), "contentTypeId", contentTypeId))
                .andRespond(withSuccess(responseWithItems(introJson), MediaType.APPLICATION_JSON));

        KtoForeignTourDetailResponse detail =
                service.getDetail(language, "foreign-1", contentTypeId);
        server.verify();
        return detail;
    }

    @Test
    void usesTreatmenuOnlyWhenFirstmenuIsBlankAndLeavesMissingFieldsNull() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoForeignTourService service = service(builder,"sample-key");
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"eng-82","title":"Haeok (해옥)","overview":null}
                """), MediaType.APPLICATION_JSON));
        server.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"eng-82","contenttypeid":"82","firstmenu":"  ",
                 "treatmenu":"Matcha latte","opentimefood":"","restdatefood":null}
                """), MediaType.APPLICATION_JSON));

        KtoForeignTourDetailResponse result = service.getDetail(KtoForeignLanguage.ENGLISH,"eng-82", "82");

        assertThat(result.mainMenu()).isEqualTo("Matcha latte");
        assertThat(result.openingHours()).isNull();
        assertThat(result.closedDays()).isNull();
        server.verify();
    }

    @Test
    void keepsTitleAndOverviewWhenTheIntroCallFailsOrIsEmpty() {
        RestClient.Builder failingBuilder = RestClient.builder();
        MockRestServiceServer failingServer = MockRestServiceServer.bindTo(failingBuilder).build();
        KtoForeignTourService failing = service(failingBuilder, "sample-key");
        failingServer.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"eng-82","title":"Daepunggwan (대풍관)","overview":"Seafood"}
                """), MediaType.APPLICATION_JSON));
        failingServer.expect(request -> { }).andRespond(withServerError());

        KtoForeignTourDetailResponse failed = failing.getDetail(KtoForeignLanguage.ENGLISH, "eng-82", "82");

        assertThat(failed.title()).isEqualTo("Daepunggwan");
        assertThat(failed.overview()).isEqualTo("Seafood");
        assertThat(failed.mainMenu()).isNull();
        assertThat(failed.openingHours()).isNull();
        assertThat(failed.closedDays()).isNull();
        failingServer.verify();

        // 영문 상세가 아예 없는 경우(가고파식당처럼)도 같은 결과다
        RestClient.Builder emptyBuilder = RestClient.builder();
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(emptyBuilder).build();
        KtoForeignTourService empty = service(emptyBuilder, "sample-key");
        emptyServer.expect(request -> { }).andRespond(withSuccess(responseWithItems("""
                {"contentid":"eng-82","title":"Daepunggwan (대풍관)","overview":"Seafood"}
                """), MediaType.APPLICATION_JSON));
        emptyServer.expect(request -> { }).andRespond(
                withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

        KtoForeignTourDetailResponse blank = empty.getDetail(KtoForeignLanguage.ENGLISH, "eng-82", "82");

        assertThat(blank.title()).isEqualTo("Daepunggwan");
        assertThat(blank.mainMenu()).isNull();
        emptyServer.verify();
    }

    private KtoForeignTourService service(RestClient.Builder builder, String apiKey) {
        return new KtoForeignTourService(builder, new ObjectMapper(), apiKey,
                "https://kto.example.test/EngService2",
                "https://kto.example.test/JpnService2",
                "https://kto.example.test/ChsService2",
                "https://kto.example.test/ChtService2");
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

    private void assertIntroRequest(URI uri, String contentId, String contentTypeId) {
        assertThat(uri.getPath()).isEqualTo("/EngService2/detailIntro2");
        assertDecodedQuery(uri, "contentId", contentId);
        assertDecodedQuery(uri, "contentTypeId", contentTypeId);
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
