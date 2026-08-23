package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A 일정 조회/등록 계약.
 * 컬럼명은 docs/db/travel_diary_schema_reference.md 의 실제 구조와 일치해야 한다.
 */
class TravelPlanItemMapperContractTest {

    @Test
    void everyDeclaredMapperMethodHasAStatement() throws IOException {
        String mapper = mapperXml();
        String mapperInterface = Files.readString(
                Path.of("src/main/java/com/example/travlediary/repository/travelplan/"
                        + "TravelPlanItemMapper.java"),
                StandardCharsets.UTF_8);

        assertThat(mapper).contains(
                "namespace=\"com.example.travlediary.repository.travelplan.TravelPlanItemMapper\"");
        for (String id : new String[]{"findByDayId", "findMaxDisplayOrder", "insertItem"}) {
            assertThat(mapperInterface).as("interface declares %s", id).contains(id);
            assertThat(mapper).as("xml defines %s", id).contains("id=\"" + id + "\"");
        }
    }

    @Test
    void itemsComeBackInDisplayOrder() throws IOException {
        String select = between(mapperXml(), "<select id=\"findByDayId\"", "</select>");

        assertThat(select)
                .contains("FROM travel_plan_items")
                .contains("WHERE travel_plan_day_id = #{travelPlanDayId}")
                .contains("ORDER BY display_order ASC, id ASC")
                .doesNotContain("${");
    }

    @Test
    void theLastOrderIsZeroWhenTheDayIsEmpty() throws IOException {
        String select = between(mapperXml(), "<select id=\"findMaxDisplayOrder\"", "</select>");

        assertThat(select)
                .contains("resultType=\"int\"")
                .contains("SELECT COALESCE(MAX(display_order), 0)")
                .contains("FROM travel_plan_items")
                .contains("WHERE travel_plan_day_id = #{travelPlanDayId}");
    }

    @Test
    void insertWritesOnlyTheColumnsThisStageOwns() throws IOException {
        String insert = between(mapperXml(), "<insert id=\"insertItem\"", "</insert>");

        assertThat(insert)
                .contains("useGeneratedKeys=\"true\"")
                .contains("keyProperty=\"id\"")
                .contains("INSERT INTO travel_plan_items")
                .contains("travel_plan_day_id, content, tag, display_order, created_by_member_id")
                .contains("#{travelPlanDayId}, #{content}, #{tag}, #{displayOrder}, "
                        + "#{createdByMemberId}");
        // version / created_at / updated_at 은 DB DEFAULT 에 맡긴다
        assertThat(insert)
                .doesNotContain("version")
                .doesNotContain("created_at")
                .doesNotContain("updated_at")
                .doesNotContain("${");
    }

    @Test
    void itemColumnsExistInTheSchemaReference() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String items = between(schema, "CREATE TABLE `travel_plan_items`", ") ENGINE=InnoDB");

        for (String column : new String[]{
                "travel_plan_day_id", "content", "tag", "display_order",
                "created_by_member_id", "version"}) {
            assertThat(items).as("travel_plan_items.%s", column).contains("`" + column + "`");
        }
    }

    @Test
    void theDayLookupIsScopedToItsPlan() throws IOException {
        String select = between(resource("/mapper/TravelPlanMapper.xml"),
                "<select id=\"findDayByPlanAndId\"", "</select>");

        // 다른 방의 dayId 를 섞어 넣어도 통과하지 못하게 두 조건을 모두 건다
        assertThat(select)
                .contains("FROM travel_plan_days")
                .contains("WHERE id = #{dayId}")
                .contains("AND travel_plan_id = #{travelPlanId}")
                .doesNotContain("${");
    }

    private String mapperXml() throws IOException {
        return resource("/mapper/TravelPlanItemMapper.xml");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
