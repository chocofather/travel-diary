package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoPhotoSearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KtoPhotoGalleryServiceTest {

    @Test
    void searchesKoreanKeywordAndMapsKtoResponseToAdminResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoPhotoGalleryService service = service(builder, "sample-key");
        String json = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                  "numOfRows":12,"pageNo":1,"totalCount":1,"items":{"item":[{
                    "galContentId":"123","galContentTypeId":"17","galTitle":"경복궁 야경",
                    "galWebImageUrl":"https://images.example.test/gyeongbokgung.jpg",
                    "galPhotographyMonth":"202501","galPhotographyLocation":"서울 종로구",
                    "galPhotographer":"한국관광공사","galSearchKeyword":"경복궁,궁궐",
                    "galCreatedtime":"20250101120000","galModifiedtime":"20250102120000"
                  }]}}}}
                """;

        server.expect(request -> assertRequestParameters(request.getURI(), "경복궁"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        KtoPhotoSearchResponse response = service.search("경복궁", 1, 12);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.numOfRows()).isEqualTo(12);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.externalContentId()).isEqualTo("123");
            assertThat(item.title()).isEqualTo("경복궁 야경");
            assertThat(item.imageUrl()).isEqualTo("https://images.example.test/gyeongbokgung.jpg");
            assertThat(item.photographyMonth()).isEqualTo("202501");
            assertThat(item.photographyLocation()).isEqualTo("서울 종로구");
            assertThat(item.photographer()).isEqualTo("한국관광공사");
            assertThat(item.searchKeyword()).isEqualTo("경복궁,궁궐");
            assertThat(item.createdTime()).isEqualTo("20250101120000");
            assertThat(item.modifiedTime()).isEqualTo("20250102120000");
            assertThat(item.sourceType()).isEqualTo("KTO_PHOTO_GALLERY");
            assertThat(item.sourceName()).isEqualTo("한국관광공사");
            assertThat(item.licenseType()).isEqualTo("KOGL_TYPE_1");
            assertThat(item.licenseLabel()).isEqualTo("공공누리 제1유형");
        });
        server.verify();
    }

    @Test
    void preservesAnAlreadyPercentEncodedGeneralKeyInTheRawQuery() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String encodedKey = "synthetic%2Bkey%2Fwith%3Dreserved";
        KtoPhotoGalleryService service = service(builder, encodedKey);

        server.expect(request -> {
                    assertRequestParameters(request.getURI(), "경복궁");
                    assertThat(rawQueryValue(request.getURI(), "serviceKey")).isEqualTo(encodedKey);
                })
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

        assertThat(service.search("경복궁", 1, 12).items()).isEmpty();
        server.verify();
    }

    @Test
    void handlesAnEmptyItemsValueAsAnEmptyResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoPhotoGalleryService service = service(builder, "sample-key");

        server.expect(request -> { })
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

        KtoPhotoSearchResponse response = service.search("경복궁", 2, 12);

        assertThat(response.pageNo()).isEqualTo(2);
        assertThat(response.numOfRows()).isEqualTo(12);
        assertThat(response.totalCount()).isZero();
        assertThat(response.items()).isEmpty();
        server.verify();
    }

    @Test
    void handlesAnAbsentItemsValueAsAnEmptyResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoPhotoGalleryService service = service(builder, "sample-key");
        String json = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                  "numOfRows":12,"pageNo":1,"totalCount":0}}}
                """;

        server.expect(request -> { }).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThat(service.search("경복궁", 1, 12).items()).isEmpty();
        server.verify();
    }

    @Test
    void rejectsMissingKeyBeforeMakingAnHttpRequest() {
        KtoPhotoGalleryService service = service(RestClient.builder(), "   ");

        assertThatThrownBy(() -> service.search("경복궁", 1, 12))
                .isInstanceOf(KtoPhotoApiException.class)
                .hasMessage("관광사진 API 인증키가 설정되지 않았습니다.");
    }

    @Test
    void keepsApiErrorAndTransportFailureMessagesFreeOfTheServiceKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String syntheticKey = "synthetic%2Bsecret%2Fmust-not-appear%3D";
        KtoPhotoGalleryService service = service(builder, syntheticKey);
        String apiError = """
                {"response":{"header":{"resultCode":"20","resultMsg":"SERVICE_KEY_IS_NULL"},"body":{
                  "numOfRows":12,"pageNo":1,"totalCount":0,"items":""}}}
                """;

        server.expect(request -> { })
                .andRespond(withSuccess(apiError, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.search("경복궁", 1, 12))
                .isInstanceOf(KtoPhotoApiException.class)
                .hasMessage("관광사진 검색 서비스를 이용할 수 없습니다.")
                .hasMessageNotContaining(syntheticKey);

        RestClient.Builder failureBuilder = RestClient.builder();
        MockRestServiceServer failureServer = MockRestServiceServer.bindTo(failureBuilder).build();
        KtoPhotoGalleryService failureService = service(failureBuilder, syntheticKey);
        failureServer.expect(request -> { }).andRespond(withServerError());

        assertThatThrownBy(() -> failureService.search("경복궁", 1, 12))
                .isInstanceOf(KtoPhotoApiException.class)
                .hasMessage("관광사진 검색 서비스를 이용할 수 없습니다.")
                .hasMessageNotContaining(syntheticKey);
        failureServer.verify();
    }

    @Test
    void convertsUnexpectedJsonToASafeUpstreamFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoPhotoGalleryService service = service(builder, "sample-key");

        server.expect(request -> { }).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.search("경복궁", 1, 12))
                .isInstanceOf(KtoPhotoApiException.class)
                .hasMessage("관광사진 검색 서비스를 이용할 수 없습니다.");
        server.verify();
    }

    private KtoPhotoGalleryService service(RestClient.Builder builder, String apiKey) {
        return new KtoPhotoGalleryService(builder, new ObjectMapper(), apiKey,
                "https://kto.example.test/PhotoGalleryService1");
    }

    private void assertRequestParameters(URI uri, String expectedKeyword) {
        assertPath(uri, "/PhotoGalleryService1/gallerySearchList1");
        assertQueryValue(uri, "keyword", expectedKeyword);
        assertQueryValue(uri, "MobileOS", "ETC");
        assertQueryValue(uri, "MobileApp", "TravelDiary");
        assertQueryValue(uri, "pageNo", "1");
        assertQueryValue(uri, "numOfRows", "12");
        assertQueryValue(uri, "_type", "json");
    }

    private void assertPath(URI uri, String expectedPath) {
        if (!expectedPath.equals(uri.getPath())) {
            throw new AssertionError("unexpected KTO request path");
        }
    }

    private void assertQueryValue(URI uri, String name, String expectedValue) {
        String rawValue = rawQueryValue(uri, name);
        String decodedValue = rawValue == null ? null : UriUtils.decode(rawValue, StandardCharsets.UTF_8);
        if (!expectedValue.equals(decodedValue)) {
            throw new AssertionError("unexpected KTO request query parameter: " + name);
        }
    }

    private String rawQueryValue(URI uri, String name) {
        String prefix = name + "=";
        return List.of(uri.getRawQuery().split("&")).stream()
                .filter(part -> part.startsWith(prefix))
                .map(part -> part.substring(prefix.length()))
                .findFirst()
                .orElse(null);
    }

    private String emptyResponse() {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                  "numOfRows":12,"pageNo":2,"totalCount":0,"items":""}}}
                """;
    }
}
