package com.example.travlediary.repository;

import com.example.travlediary.model.DestinationType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
    void masterMappingCanBeWrittenAndReadPerAmenity() throws IOException {
        String mapper = mapperXml();

        // 매핑 1건 등록. destination_type 은 DestinationType enum 이름을 그대로 저장한다.
        assertThat(between(mapper, "<insert id=\"insertAmenityDestinationType\"", "</insert>"))
                .contains("INSERT INTO amenity_destination_types (amenity_id, destination_type)")
                .contains("VALUES (#{amenityId}, #{destinationType})")
                .doesNotContain("${");

        // 수정은 "전체 삭제 후 선택값 재삽입" 방식이므로 amenityId 기준 전체 삭제가 필요하다
        assertThat(between(mapper, "<delete id=\"deleteAmenityDestinationTypesByAmenityId\"", "</delete>"))
                .contains("DELETE FROM amenity_destination_types WHERE amenity_id = #{amenityId}");

        // 수정 화면 체크 상태 복원용 단건 조회
        assertThat(between(mapper, "<select id=\"findAmenityDestinationTypesByAmenityId\"", "</select>"))
                .contains("SELECT amenity_id, destination_type")
                .contains("FROM amenity_destination_types")
                .contains("WHERE amenity_id = #{amenityId}")
                .doesNotContain("${");
    }

    @Test
    void destinationTypeColumnMatchesTheEnumNameContractInTheSchema() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String table = between(schema,
                "CREATE TABLE `amenity_destination_types`", ") ENGINE=InnoDB");

        // enum 이름을 문자열로 담는 컬럼이며 복합 PK 라 별도 id/부가 컬럼이 없다
        assertThat(table)
                .contains("`amenity_id` int NOT NULL")
                .contains("`destination_type` varchar(30) NOT NULL")
                .contains("PRIMARY KEY (`amenity_id`,`destination_type`)");
        // DestinationType enum 값 중 가장 긴 이름도 varchar(30) 안에 들어간다
        for (DestinationType type : DestinationType.values()) {
            assertThat(type.name().length()).as("enum %s", type).isLessThanOrEqualTo(30);
        }
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
