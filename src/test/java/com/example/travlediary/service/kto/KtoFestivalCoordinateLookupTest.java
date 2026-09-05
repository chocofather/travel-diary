package com.example.travlediary.service.kto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 국문 축제 contentId 로 좌표만 복구한다.
 *
 * <p>festival_info 에 좌표 컬럼을 두지 않는 대신, 외국어 매칭이 필요할 때만
 * 기존 국문 detailCommon2 호출을 빌려 쓴다. 값은 저장하지 않는다.
 */
class KtoFestivalCoordinateLookupTest {

    @Test
    void coordinatesComeFromTheKoreanDetailCommonCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoFestivalService service = service(builder);

        server.expect(request -> {
            assertThat(request.getURI().getPath()).isEqualTo("/KorService2/detailCommon2");
            assertDecodedQuery(request.getURI(), "contentId", "2648460");
            // 좌표만 필요하므로 유형별 상세는 부르지 않는다.
            assertThat(request.getURI().getQuery()).doesNotContain("contentTypeId");
        }).andRespond(withSuccess(detailBody("""
                "contentid":"2648460","contenttypeid":"15","title":"경복궁 별빛야행",
                "mapx":"126.9769930325","mapy":"37.5788222356"
                """), MediaType.APPLICATION_JSON));

        Optional<KtoFestivalCoordinates> coordinates = service.getCoordinates("2648460");

        assertThat(coordinates).contains(
                new KtoFestivalCoordinates("126.9769930325", "37.5788222356"));
        server.verify();
    }

    @Test
    void aResponseWithoutCoordinatesYieldsNothing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoFestivalService service = service(builder);

        server.expect(request -> { }).andRespond(withSuccess(detailBody("""
                "contentid":"2648460","contenttypeid":"15","title":"경복궁 별빛야행",
                "mapx":"","mapy":"   "
                """), MediaType.APPLICATION_JSON));

        assertThat(service.getCoordinates("2648460")).isEmpty();
        server.verify();
    }

    @Test
    void anUpstreamFailureYieldsNothingRatherThanAnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoFestivalService service = service(builder);

        server.expect(request -> { }).andRespond(withServerError());

        assertThat(service.getCoordinates("2648460")).isEmpty();
        server.verify();
    }

    @Test
    void aContentIdThatIsNotAFestivalYieldsNothing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoFestivalService service = service(builder);

        // 국문 축제 유형(15)이 아니면 축제 좌표로 쓰지 않는다.
        server.expect(request -> { }).andRespond(withSuccess(detailBody("""
                "contentid":"126508","contenttypeid":"12","title":"경복궁",
                "mapx":"126.9769930325","mapy":"37.5788222356"
                """), MediaType.APPLICATION_JSON));

        assertThat(service.getCoordinates("126508")).isEmpty();
        server.verify();
    }

    @Test
    void aBlankContentIdNeverCallsTheApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KtoFestivalService service = service(builder);

        assertThat(service.getCoordinates("   ")).isEmpty();
        assertThat(service.getCoordinates(null)).isEmpty();
        server.verify();
    }

    private KtoFestivalService service(RestClient.Builder builder) {
        return new KtoFestivalService(builder, new ObjectMapper(), "sample-key",
                "https://kto.example.test/KorService2");
    }

    private void assertDecodedQuery(URI uri, String name, String expected) {
        String prefix = name + "=";
        String raw = Arrays.stream(uri.getRawQuery().split("&"))
                .filter(part -> part.startsWith(prefix))
                .map(part -> part.substring(prefix.length()))
                .findFirst()
                .orElseThrow();
        assertThat(UriUtils.decode(raw, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    private String detailBody(String itemFields) {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[{
                %s
                }]}}}}
                """.formatted(itemFields);
    }
}
