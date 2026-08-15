package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * destinations.google_place_id 저장/조회 계약.
 * 해외 지도(Maps Embed API) 장소 식별용 선택 컬럼이라 latitude/longitude 와 같은 지점에만 반영한다.
 */
class DestinationPlaceIdMapperContractTest {

    @Test
    void schemaDocumentMatchesTheRealColumnPosition() throws IOException {
        String destinations = between(readFile("docs/db/travel_diary_schema_reference.md"),
                "CREATE TABLE `destinations`", "ENGINE=InnoDB");

        assertThat(destinations).contains("`google_place_id` varchar(255) DEFAULT NULL");
        // 실제 DB 와 동일하게 longitude 다음이다
        assertThat(destinations.indexOf("`google_place_id`"))
                .isGreaterThan(destinations.indexOf("`longitude`"))
                .isLessThan(destinations.indexOf("`created_at`"));
    }

    @Test
    void modelExposesTheColumnThroughLombok() throws IOException {
        assertThat(readFile("src/main/java/com/example/travlediary/model/Destination.java"))
                .contains("private String googlePlaceId")
                .contains("@Data");
    }

    @Test
    void detailReadAndWriteStatementsCarryTheColumn() throws IOException {
        String xml = resource("/mapper/DestinationMapper.xml");

        // 상세 조회 resultMap (SELECT 는 d.* 라 컬럼이 이미 내려온다)
        assertThat(between(xml, "<resultMap id=\"DestinationDetailMap\"", "</resultMap>"))
                .contains("<result property=\"googlePlaceId\" column=\"google_place_id\"/>");
        // 등록
        assertThat(between(xml, "<insert id=\"insertDestination\"", "</insert>"))
                .contains("google_place_id")
                .contains("#{googlePlaceId}");
        // 수정
        assertThat(between(xml, "<update id=\"updateDestination\"", "</update>"))
                .contains("google_place_id = #{googlePlaceId}");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readFile(String path) throws IOException {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
