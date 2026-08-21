package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행지 유형별 편의시설 마스터 조회 계약.
 * 사용 이력(*_amenities)이 아니라 amenity_destination_types 매핑을 읽어야 한다.
 */
class AmenityDestinationTypeMapperContractTest {

    @Test
    void destinationTypeQueryReadsTheMasterMappingTable() throws IOException {
        String select = between(mapperXml(),
                "<select id=\"findTranslationsByDestinationTypeAndLang\"", "</select>");

        assertThat(select)
                .contains("FROM amenity_destination_types adt")
                .contains("JOIN amenities a ON a.id = adt.amenity_id")
                .contains("JOIN amenity_translations t ON t.amenity_id = a.id")
                .contains("WHERE adt.destination_type = #{destinationType}")
                .contains("AND t.language_code = #{languageCode}")
                // 동적 테이블명/문자열 결합 금지
                .doesNotContain("${")
                // 사용 이력 테이블은 쓰지 않는다
                .doesNotContain("_amenities ta")
                .doesNotContain("attraction_amenities")
                .doesNotContain("restaurant_amenities");
    }

    @Test
    void existingFullListQueryStaysUntouched() throws IOException {
        String mapper = mapperXml();

        assertThat(between(mapper, "<select id=\"findTranslationsByLang\"", "</select>"))
                .contains("SELECT * FROM amenity_translations WHERE language_code = #{languageCode}");
        // 기존 dead code 는 이번 범위에서 건드리지 않는다
        assertThat(mapper).contains("<select id=\"findTranslationsByTypeAndLang\"");
    }

    private String mapperXml() throws IOException {
        return resource("/mapper/AmenityMapper.xml");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
