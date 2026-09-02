package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoFestivalAutofillResponse;
import com.example.travlediary.dto.kto.KtoFestivalAdditionalImage;
import com.example.travlediary.dto.kto.KtoFestivalImageDetail;
import com.example.travlediary.dto.kto.KtoFestivalSearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KtoFestivalServiceTest {

    @Test
    void searchesFestivalCandidatesAndMapsNewClassificationCodesToCategoryNames() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoFestivalService service = service(builder, "sample-key");
        String response = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                  "numOfRows":10,"pageNo":2,"totalCount":8,"items":{"item":[
                    {"contentid":"1","contenttypeid":"15","title":"봄 축제","eventstartdate":"20260901",
                     "eventenddate":"20260903","firstimage":"https://images.test/1.jpg",
                     "firstimage2":"https://images.test/1-small.jpg","addr1":"서울 중구","addr2":"광장",
                     "lclsSystm1":"EV","lclsSystm2":"EV01","lclsSystm3":"EV010100"},
                    {"contentid":"2","contenttypeid":"15","title":"공연","eventstartdate":"20260904",
                     "eventenddate":"20260904","lclsSystm2":"EV02","lclsSystm3":"EV020100"},
                    {"contentid":"3","contenttypeid":"15","title":"전시","eventstartdate":"20260905",
                     "eventenddate":"20260910","lclsSystm2":"EV03","lclsSystm3":"EV030100"},
                    {"contentid":"4","contenttypeid":"15","title":"박람회","eventstartdate":"20260911",
                     "eventenddate":"20260912","lclsSystm2":"EV03","lclsSystm3":"EV030200"},
                    {"contentid":"5","contenttypeid":"15","title":"대회","eventstartdate":"20260913",
                     "eventenddate":"20260913","lclsSystm2":"EV03","lclsSystm3":"EV030300"},
                    {"contentid":"6","contenttypeid":"15","title":"기타 행사","eventstartdate":"20260914",
                     "eventenddate":"20260914","lclsSystm2":"EV03","lclsSystm3":"EV030400"},
                    {"contentid":"7","contenttypeid":"15","title":"알 수 없는 행사","eventstartdate":"20260915",
                     "eventenddate":"20260915","lclsSystm2":"EV03","lclsSystm3":"EV039999"},
                    {"contentid":"8","contenttypeid":"15","title":"미분류 행사","eventstartdate":"20260916",
                     "eventenddate":"20260916"}
                  ]}}}}
                """;

        server.expect(request -> assertSearchRequest(request.getURI()))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        KtoFestivalSearchResponse result = service.search(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 2, 10);

        assertThat(result.pageNo()).isEqualTo(2);
        assertThat(result.numOfRows()).isEqualTo(10);
        assertThat(result.totalCount()).isEqualTo(8);
        assertThat(result.items()).extracting("contentId", "categoryName")
                .containsExactly(
                        tuple("1", "축제"),
                        tuple("2", "공연"),
                        tuple("3", "전시·박람회"),
                        tuple("4", "전시·박람회"),
                        tuple("5", "스포츠·대회"),
                        tuple("6", "기타행사"),
                        tuple("7", "기타행사"),
                        tuple("8", "기타행사")
                );
        assertThat(result.items().get(0)).satisfies(item -> {
            assertThat(item.title()).isEqualTo("봄 축제");
            assertThat(item.eventStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(item.eventEndDate()).isEqualTo(LocalDate.of(2026, 9, 3));
            assertThat(item.firstImage()).isEqualTo("https://images.test/1.jpg");
            assertThat(item.firstImage2()).isEqualTo("https://images.test/1-small.jpg");
            assertThat(item.address()).isEqualTo("서울 중구 광장");
            assertThat(item.lclsSystm1()).isEqualTo("EV");
            assertThat(item.lclsSystm2()).isEqualTo("EV01");
            assertThat(item.lclsSystm3()).isEqualTo("EV010100");
        });
        server.verify();
    }

    @Test
    void searchesFestivalCandidatesByKeywordWithFestivalContentTypeOnly() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoFestivalService service = service(builder, "sample-key");
        String response = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                  "numOfRows":10,"pageNo":1,"totalCount":1,"items":{"item":{
                    "contentid":"keyword-15","contenttypeid":"15","title":"경복궁 야간관람",
                    "eventstartdate":"20261001","eventenddate":"20261031",
                    "firstimage":"https://images.test/gyeongbokgung.jpg","addr1":"서울 종로구",
                    "lclsSystm2":"EV01","lclsSystm3":"EV010100"
                  }}}}}
                """;

        server.expect(request -> {
                    URI uri = request.getURI();
                    assertThat(uri.getPath()).isEqualTo("/KorService2/searchKeyword2");
                    assertDecodedQuery(uri, "keyword", "경복궁");
                    assertDecodedQuery(uri, "contentTypeId", "15");
                    assertDecodedQuery(uri, "pageNo", "1");
                    assertDecodedQuery(uri, "numOfRows", "10");
                    assertThat(uri.getQuery()).doesNotContain("eventStartDate", "eventEndDate");
                })
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        KtoFestivalSearchResponse result = service.searchByKeyword(" 경복궁 ", 1, 10);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.contentId()).isEqualTo("keyword-15");
            assertThat(item.title()).isEqualTo("경복궁 야간관람");
            assertThat(item.eventStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(item.eventEndDate()).isEqualTo(LocalDate.of(2026, 10, 31));
            assertThat(item.categoryName()).isEqualTo("축제");
        });
        server.verify();
    }

    @Test
    void combinesDetailCommonAndFestivalIntroForAutofill() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoFestivalService service = service(builder, "sample-key");
        String common = detailBody("""
                "contentid":"festival-15","contenttypeid":"15","title":"서울 문화 축제",
                "addr1":"서울 종로구","addr2":"광화문광장",
                "firstimage":"https://images.test/main.jpg","firstimage2":"https://images.test/thumb.jpg",
                "overview":"행사 소개<br><b>두 번째 줄</b><script>alert(1)</script>",
                "homepage":"<a href=\\\"https://festival.example/\\\">공식 홈페이지</a>",
                "tel":"02-1111-2222","lclsSystm1":"EV","lclsSystm2":"EV03","lclsSystm3":"EV030200"
                """);
        String intro = detailBody("""
                "contentid":"festival-15","contenttypeid":"15","eventstartdate":"20261009",
                "eventenddate":"20261012","eventplace":"광화문광장 일대",
                "playtime":"10:00~21:00","usetimefestival":"무료",
                "sponsor1":"서울시","sponsor1tel":"02-120","sponsor2":"축제위원회",
                "sponsor2tel":"02-3333-4444",
                "eventhomepage":"<a href=\\\"https://event.example/\\\">행사 홈페이지</a>"
                """);

        server.expect(request -> assertDetailRequest(request.getURI(), "/detailCommon2", false))
                .andRespond(withSuccess(common, MediaType.APPLICATION_JSON));
        server.expect(request -> assertDetailRequest(request.getURI(), "/detailIntro2", true))
                .andRespond(withSuccess(intro, MediaType.APPLICATION_JSON));

        KtoFestivalAutofillResponse result = service.getDetail("festival-15");

        assertThat(result.contentId()).isEqualTo("festival-15");
        assertThat(result.title()).isEqualTo("서울 문화 축제");
        assertThat(result.eventStartDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(result.eventEndDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(result.firstImage()).isEqualTo("https://images.test/main.jpg");
        assertThat(result.firstImage2()).isEqualTo("https://images.test/thumb.jpg");
        assertThat(result.address()).isEqualTo("서울 종로구 광화문광장");
        assertThat(result.eventPlace()).isEqualTo("광화문광장 일대");
        assertThat(result.overview()).isEqualTo("행사 소개\n두 번째 줄");
        assertThat(result.playTime()).isEqualTo("10:00~21:00");
        assertThat(result.useTimeFestival()).isEqualTo("무료");
        assertThat(result.sponsor1()).isEqualTo("서울시");
        assertThat(result.sponsor1Tel()).isEqualTo("02-120");
        assertThat(result.sponsor2()).isEqualTo("축제위원회");
        assertThat(result.sponsor2Tel()).isEqualTo("02-3333-4444");
        assertThat(result.homepage()).isEqualTo("https://festival.example/");
        assertThat(result.eventHomepage()).isEqualTo("https://event.example/");
        assertThat(result.tel()).isEqualTo("02-1111-2222");
        assertThat(result.lclsSystm1()).isEqualTo("EV");
        assertThat(result.lclsSystm2()).isEqualTo("EV03");
        assertThat(result.lclsSystm3()).isEqualTo("EV030200");
        assertThat(result.categoryName()).isEqualTo("전시·박람회");
        server.verify();
    }

    @Test
    void readsDetailCommonAgainForFestivalImageRegistrationAndKeepsCopyrightCode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoFestivalService service = service(builder, "sample-key");
        String common = detailBody("""
                "contentid":"2648460","contenttypeid":"15","title":"경복궁 별빛야행",
                "firstimage":"https://tong.visitkorea.or.kr/cms2/website/75/gyeongbokgung.jpg",
                "cpyrhtDivCd":"Type3"
                """);

        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/KorService2/detailCommon2");
                    assertDecodedQuery(request.getURI(), "contentId", "2648460");
                    assertThat(request.getURI().getQuery()).doesNotContain("contentTypeId");
                })
                .andRespond(withSuccess(common, MediaType.APPLICATION_JSON));

        KtoFestivalImageDetail result = service.getImageDetail("2648460");

        assertThat(result.contentId()).isEqualTo("2648460");
        assertThat(result.title()).isEqualTo("경복궁 별빛야행");
        assertThat(result.firstImage())
                .isEqualTo("https://tong.visitkorea.or.kr/cms2/website/75/gyeongbokgung.jpg");
        assertThat(result.copyrightDivisionCode()).isEqualTo("Type3");
        server.verify();
    }

    @Test
    void readsAllDetailImagePagesWithPerImageCopyrightMetadataInResponseOrder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoFestivalService service = service(builder, "sample-key");
        String firstPage = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                  "numOfRows":2,"pageNo":1,"totalCount":3,"items":{"item":[
                    {"contentid":"2648460","imgname":"별빛야행 전경",
                     "originimgurl":"https://tong.visitkorea.or.kr/cms/resource/35/additional-1.jpg",
                     "smallimageurl":"https://tong.visitkorea.or.kr/cms/resource/35/additional-1-thumb.jpg",
                     "serialnum":"1","cpyrhtDivCd":"Type1"},
                    {"contentid":"2648460","imgname":"별빛야행 공연",
                     "originimgurl":"https://tong.visitkorea.or.kr/cms/resource/35/additional-2.jpg",
                     "serialnum":"2","cpyrhtDivCd":"Type3"}
                  ]}}}}
                """;
        String secondPage = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                  "numOfRows":2,"pageNo":2,"totalCount":3,"items":{"item":{
                    "contentid":"2648460","imgname":"저작권 미확인 이미지",
                    "originimgurl":"https://tong.visitkorea.or.kr/cms/resource/35/additional-3.jpg",
                    "serialnum":"3","cpyrhtDivCd":""
                  }}}}}
                """;

        server.expect(request -> assertDetailImageRequest(request.getURI(), 1, 2))
                .andRespond(withSuccess(firstPage, MediaType.APPLICATION_JSON));
        server.expect(request -> assertDetailImageRequest(request.getURI(), 2, 2))
                .andRespond(withSuccess(secondPage, MediaType.APPLICATION_JSON));

        assertThat(service.getAdditionalImages("2648460", 2))
                .extracting(
                        KtoFestivalAdditionalImage::contentId,
                        KtoFestivalAdditionalImage::imageName,
                        KtoFestivalAdditionalImage::originalImageUrl,
                        KtoFestivalAdditionalImage::serialNumber,
                        KtoFestivalAdditionalImage::copyrightDivisionCode)
                .containsExactly(
                        tuple("2648460", "별빛야행 전경",
                                "https://tong.visitkorea.or.kr/cms/resource/35/additional-1.jpg", "1", "Type1"),
                        tuple("2648460", "별빛야행 공연",
                                "https://tong.visitkorea.or.kr/cms/resource/35/additional-2.jpg", "2", "Type3"),
                        tuple("2648460", "저작권 미확인 이미지",
                                "https://tong.visitkorea.or.kr/cms/resource/35/additional-3.jpg", "3", null));
        server.verify();
    }

    @Test
    void buildsThumbnailCandidatesFromMainAndDetailImagesWithoutSavingThumbnailsOrTrustingUrls() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoFestivalService service = service(builder, "sample-key");
        String common = detailBody("""
                "contentid":"2648460","contenttypeid":"15","title":"경복궁 별빛야행",
                "firstimage":"https://tong.visitkorea.or.kr/cms/resource/35/main.jpg",
                "firstimage2":"https://tong.visitkorea.or.kr/cms/resource/35/main-small.jpg",
                "cpyrhtDivCd":"Type1"
                """);
        String detailImages = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                  "numOfRows":100,"pageNo":1,"totalCount":4,"items":{"item":[
                    {"contentid":"2648460","imgname":"대표사진 중복",
                     "originimgurl":"https://tong.visitkorea.or.kr/cms/resource/35/main.jpg",
                     "smallimageurl":"https://tong.visitkorea.or.kr/cms/resource/35/main-thumb.jpg",
                     "serialnum":"1","cpyrhtDivCd":"Type1"},
                    {"contentid":"2648460","imgname":"공식 포스터",
                     "originimgurl":"https://tong.visitkorea.or.kr/cms/resource/35/poster.jpg",
                     "serialnum":"2","cpyrhtDivCd":"Type3"},
                    {"contentid":"2648460","imgname":"미지원 이미지",
                     "originimgurl":"https://tong.visitkorea.or.kr/cms/resource/35/blocked.jpg",
                     "serialnum":"3","cpyrhtDivCd":"Type2"},
                    {"contentid":"2648460","imgname":"식별값 없는 이미지",
                     "originimgurl":"https://tong.visitkorea.or.kr/cms/resource/35/no-serial.jpg",
                     "cpyrhtDivCd":"Type1"}
                  ]}}}}
                """;

        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/KorService2/detailCommon2");
                    assertDecodedQuery(request.getURI(), "contentId", "2648460");
                })
                .andRespond(withSuccess(common, MediaType.APPLICATION_JSON));
        server.expect(request -> assertDetailImageRequest(request.getURI(), 1, 100))
                .andRespond(withSuccess(detailImages, MediaType.APPLICATION_JSON));

        assertThat(service.getThumbnailCandidates("2648460"))
                .extracting("selectionKey", "imageUrl", "imageName", "imageRole", "licenseType",
                        "selectable", "unavailableReason")
                .containsExactly(
                        tuple("MAIN", "https://tong.visitkorea.or.kr/cms/resource/35/main.jpg",
                                "경복궁 별빛야행", "대표사진", "KOGL_TYPE_1", true, null),
                        tuple("DETAIL:2", "https://tong.visitkorea.or.kr/cms/resource/35/poster.jpg",
                                "공식 포스터", "추가사진", "KOGL_TYPE_3", true, null),
                        tuple("DETAIL:3", "https://tong.visitkorea.or.kr/cms/resource/35/blocked.jpg",
                                "미지원 이미지", "추가사진", null, false, "지원하지 않는 저작권 유형입니다."),
                        tuple(null, "https://tong.visitkorea.or.kr/cms/resource/35/no-serial.jpg",
                                "식별값 없는 이미지", "추가사진", "KOGL_TYPE_1", false,
                                "이미지 식별값을 확인할 수 없습니다."));
        server.verify();
    }

    @Test
    void rejectsMalformedTourApiDatesAsAnUpstreamFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoFestivalService service = service(builder, "sample-key");
        String response = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                  "numOfRows":10,"pageNo":1,"totalCount":1,"items":{"item":[
                    {"contentid":"bad-date","contenttypeid":"15","title":"날짜 오류 행사",
                     "eventstartdate":"20260230","eventenddate":"20260301"}
                  ]}}}}
                """;
        server.expect(request -> { }).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.search(LocalDate.of(2026, 2, 1), null, 1, 10))
                .isInstanceOf(KtoTourApiException.class)
                .hasMessage("관광정보를 불러오지 못했습니다.");
        server.verify();
    }

    @Test
    void rejectsMissingApiKeyBeforeAnyHttpCall() {
        KtoFestivalService missingKeyService = service(RestClient.builder(), "  ");

        assertThatThrownBy(() -> missingKeyService.search(LocalDate.of(2026, 9, 1), null, 1, 10))
                .isInstanceOf(KtoTourApiException.class)
                .hasMessage("TourAPI 인증키가 설정되지 않았습니다.");
        assertThatThrownBy(() -> missingKeyService.searchByKeyword("경복궁", 1, 10))
                .isInstanceOf(KtoTourApiException.class)
                .hasMessage("TourAPI 인증키가 설정되지 않았습니다.");
    }

    @Test
    void convertsHttpFailureToASafeUpstreamError() {
        RestClient.Builder httpBuilder = RestClient.builder();
        MockRestServiceServer httpServer = MockRestServiceServer.bindTo(httpBuilder).build();
        String encodedKey = "synthetic%2Bsecret%3D";
        KtoFestivalService httpFailureService = service(httpBuilder, encodedKey);
        httpServer.expect(request -> { }).andRespond(withServerError());
        assertThatThrownBy(() -> httpFailureService.search(LocalDate.of(2026, 9, 1), null, 1, 10))
                .isInstanceOf(KtoTourApiException.class)
                .hasMessage("관광정보를 불러오지 못했습니다.")
                .hasMessageNotContaining(encodedKey);
        httpServer.verify();
    }

    @Test
    void rejectsTourApiResponseWithANonSuccessResultCode() {
        RestClient.Builder apiBuilder = RestClient.builder();
        MockRestServiceServer apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        KtoFestivalService rejectedResponseService = service(apiBuilder, "sample-key");
        String rejected = """
                {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"},
                "body":{"numOfRows":0,"pageNo":1,"totalCount":0,"items":""}}}
                """;
        apiServer.expect(request -> { }).andRespond(withSuccess(rejected, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> rejectedResponseService.search(LocalDate.of(2026, 9, 1), null, 1, 10))
                .isInstanceOf(KtoTourApiException.class)
                .hasMessage("관광정보를 불러오지 못했습니다.");
        apiServer.verify();
    }

    private KtoFestivalService service(RestClient.Builder builder, String apiKey) {
        return new KtoFestivalService(builder, new ObjectMapper(), apiKey,
                "https://kto.example.test/KorService2");
    }

    private void assertSearchRequest(URI uri) {
        assertThat(uri.getPath()).isEqualTo("/KorService2/searchFestival2");
        assertDecodedQuery(uri, "eventStartDate", "20260901");
        assertDecodedQuery(uri, "eventEndDate", "20260930");
        assertDecodedQuery(uri, "pageNo", "2");
        assertDecodedQuery(uri, "numOfRows", "10");
        assertDecodedQuery(uri, "MobileOS", "ETC");
        assertDecodedQuery(uri, "MobileApp", "TravelDiary");
        assertDecodedQuery(uri, "_type", "json");
        assertThat(rawQueryValue(uri, "serviceKey")).isEqualTo("sample-key");
        assertThat(uri.getQuery()).doesNotContain("contentTypeId");
    }

    private void assertDetailRequest(URI uri, String expectedPath, boolean intro) {
        assertThat(uri.getPath()).isEqualTo("/KorService2" + expectedPath);
        assertDecodedQuery(uri, "contentId", "festival-15");
        if (intro) {
            assertDecodedQuery(uri, "contentTypeId", "15");
        } else {
            assertThat(uri.getQuery()).doesNotContain("contentTypeId");
        }
    }

    private void assertDetailImageRequest(URI uri, int pageNo, int numOfRows) {
        assertThat(uri.getPath()).isEqualTo("/KorService2/detailImage2");
        assertDecodedQuery(uri, "contentId", "2648460");
        assertDecodedQuery(uri, "imageYN", "Y");
        assertDecodedQuery(uri, "pageNo", String.valueOf(pageNo));
        assertDecodedQuery(uri, "numOfRows", String.valueOf(numOfRows));
        assertThat(uri.getQuery()).doesNotContain("subImageYN", "firstImage2");
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

    private String detailBody(String itemFields) {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[{
                %s
                }]}}}}
                """.formatted(itemFields);
    }
}
