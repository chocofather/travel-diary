package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행지 유형별 카테고리 마스터 조회 계약.
 * 사용 이력(destination_categories)이 아니라 category_destination_types 매핑을 읽어야 한다.
 */
class CategoryDestinationTypeMapperContractTest {

    @Test
    void destinationTypeQueryReadsTheMasterMappingTable() throws IOException {
        String select = between(mapperXml(), "<select id=\"findByDestinationType\"", "</select>");

        assertThat(select)
                .contains("FROM category_destination_types cdt")
                .contains("JOIN categories c ON c.id = cdt.category_id")
                .contains("WHERE cdt.destination_type = #{destinationType}")
                // 기존 전체 목록과 같은 정렬(id)
                .contains("ORDER BY c.id")
                // 동적 SQL / 사용 이력 테이블 금지
                .doesNotContain("${")
                .doesNotContain("destination_categories");
    }

    @Test
    void existingCategoryQueriesStayUntouched() throws IOException {
        String mapper = mapperXml();

        assertThat(between(mapper, "<select id=\"findAll\"", "</select>"))
                .contains("SELECT id, name FROM categories")
                .contains("ORDER BY id");
        assertThat(mapper)
                .contains("<select id=\"getCategoryNamesByDestinationId\"")
                .contains("<insert id=\"insert\"")
                .contains("<delete id=\"deleteById\"");
    }

    private String mapperXml() throws IOException {
        return resource("/mapper/CategoryMapper.xml");
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
