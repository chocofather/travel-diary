package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A 일정에 붙는 대안(B/C) 조회/등록 계약.
 * 컬럼명은 docs/db/travel_diary_schema_reference.md 의 실제 구조와 일치해야 한다.
 */
class TravelPlanAlternativeMapperContractTest {

    @Test
    void everyDeclaredMapperMethodHasAStatement() throws IOException {
        String mapper = mapperXml();
        String mapperInterface = Files.readString(
                Path.of("src/main/java/com/example/travlediary/repository/travelplan/"
                        + "TravelPlanAlternativeMapper.java"),
                StandardCharsets.UTF_8);

        assertThat(mapper).contains("namespace=\"com.example.travlediary.repository.travelplan."
                + "TravelPlanAlternativeMapper\"");
        for (String id : new String[]{
                "findByItemId", "findByPlanId", "countByItemId", "findByIdAndItemId",
                "findByItemIdAndOrder", "insertAlternative", "updateWithVersion",
                "deleteByIdAndItemId", "updateOrderByIdAndItemId"}) {
            assertThat(mapperInterface).as("interface declares %s", id).contains(id);
            assertThat(mapper).as("xml defines %s", id).contains("id=\"" + id + "\"");
        }
    }

    @Test
    void alternativeColumnsExistInTheSchemaReference() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String table = between(schema,
                "CREATE TABLE `travel_plan_item_alternatives`", ") ENGINE=InnoDB");

        for (String column : new String[]{
                "travel_plan_item_id", "alternative_order", "condition_label", "content", "tag",
                "created_by_member_id", "version"}) {
            assertThat(table).as("travel_plan_item_alternatives.%s", column)
                    .contains("`" + column + "`");
        }
        // B=1 / C=2 라는 규칙은 DB 제약으로도 걸려 있다
        assertThat(table).contains("`alternative_order` in (1,2)");
    }

    @Test
    void alternativesComeBackInOrderSoThatOneIsBAndTwoIsC() throws IOException {
        String select = between(mapperXml(), "<select id=\"findByItemId\"", "</select>");

        assertThat(select)
                .contains("FROM travel_plan_item_alternatives")
                .contains("WHERE travel_plan_item_id = #{travelPlanItemId}")
                .contains("ORDER BY alternative_order ASC")
                .doesNotContain("${");
    }

    @Test
    void thePlanPageReadsEveryAlternativeInOneQuery() throws IOException {
        String select = between(mapperXml(), "<select id=\"findByPlanId\"", "</select>");

        // 일정 수만큼 조회가 나가지 않도록 방 단위로 한 번에 읽는다
        assertThat(select)
                .contains("FROM travel_plan_item_alternatives a")
                .contains("JOIN travel_plan_items i ON i.id = a.travel_plan_item_id")
                .contains("JOIN travel_plan_days d ON d.id = i.travel_plan_day_id")
                .contains("WHERE d.travel_plan_id = #{travelPlanId}")
                .contains("ORDER BY a.travel_plan_item_id ASC, a.alternative_order ASC")
                .doesNotContain("${");
    }

    @Test
    void theCountBacksTheMaximumOfTwoOnTheServer() throws IOException {
        String select = between(mapperXml(), "<select id=\"countByItemId\"", "</select>");

        assertThat(select)
                .contains("resultType=\"int\"")
                .contains("SELECT COUNT(*)")
                .contains("FROM travel_plan_item_alternatives")
                .contains("WHERE travel_plan_item_id = #{travelPlanItemId}");
    }

    @Test
    void insertWritesOnlyTheColumnsThisStageOwns() throws IOException {
        String insert = between(mapperXml(), "<insert id=\"insertAlternative\"", "</insert>");

        assertThat(insert)
                .contains("useGeneratedKeys=\"true\"")
                .contains("keyProperty=\"id\"")
                .contains("INSERT INTO travel_plan_item_alternatives")
                .contains("travel_plan_item_id, alternative_order, condition_label, content, tag")
                .contains("created_by_member_id")
                .contains("#{travelPlanItemId}, #{alternativeOrder}, #{conditionLabel}, "
                        + "#{content}, #{tag}");
        // version / created_at / updated_at 은 DB DEFAULT 에 맡긴다
        assertThat(insert)
                .doesNotContain("version")
                .doesNotContain("created_at")
                .doesNotContain("updated_at")
                .doesNotContain("${");
    }

    @Test
    void theAlternativeLookupsAreScopedToTheirItem() throws IOException {
        String mapper = mapperXml();

        // 다른 일정의 alternativeId 를 섞어 넣어도 통과하지 못하게 두 조건을 모두 건다
        assertThat(between(mapper, "<select id=\"findByIdAndItemId\"", "</select>"))
                .contains("FROM travel_plan_item_alternatives")
                .contains("WHERE id = #{id}")
                .contains("AND travel_plan_item_id = #{travelPlanItemId}")
                .contains("alternative_order")
                .contains("version")
                .doesNotContain("${");

        assertThat(between(mapper, "<select id=\"findByItemIdAndOrder\"", "</select>"))
                .contains("WHERE travel_plan_item_id = #{travelPlanItemId}")
                .contains("AND alternative_order = #{alternativeOrder}")
                .doesNotContain("${");
    }

    @Test
    void updateOnlyLandsWhenTheVersionStillMatches() throws IOException {
        String update = between(mapperXml(), "<update id=\"updateWithVersion\"", "</update>");

        assertThat(update)
                .contains("UPDATE travel_plan_item_alternatives")
                .contains("SET condition_label = #{conditionLabel}")
                .contains("content = #{content}")
                // 낙관적 잠금: 버전이 그대로일 때만 반영되고, 성공하면 1 증가한다
                .contains("version = version + 1")
                .contains("updated_at = CURRENT_TIMESTAMP")
                .contains("WHERE id = #{id}")
                .contains("AND travel_plan_item_id = #{travelPlanItemId}")
                .contains("AND version = #{version}")
                .doesNotContain("${");
        // 수정은 자리를 옮기지 않는다
        assertThat(between(update, "SET", "WHERE")).doesNotContain("alternative_order");
    }

    @Test
    void deleteAndPromotionStayInsideTheirOwnItem() throws IOException {
        String mapper = mapperXml();

        assertThat(between(mapper, "<delete id=\"deleteByIdAndItemId\"", "</delete>"))
                .contains("DELETE FROM travel_plan_item_alternatives")
                .contains("WHERE id = #{id}")
                .contains("AND travel_plan_item_id = #{travelPlanItemId}")
                .doesNotContain("${");

        // C(2) 를 B(1) 자리로 당길 때 내용/조건/작성자는 그대로 둔다
        String promote = between(mapper, "<update id=\"updateOrderByIdAndItemId\"", "</update>");
        assertThat(promote)
                .contains("UPDATE travel_plan_item_alternatives")
                .contains("SET alternative_order = #{alternativeOrder}")
                .contains("version = version + 1")
                .contains("WHERE id = #{id}")
                .contains("AND travel_plan_item_id = #{travelPlanItemId}")
                .doesNotContain("${");
        assertThat(between(promote, "SET", "WHERE"))
                .doesNotContain("content")
                .doesNotContain("condition_label")
                .doesNotContain("created_by_member_id");
    }

    @Test
    void promotingBIntoAKeepsTheRowAndTakesOverItsAuthor() throws IOException {
        String update = between(resource("/mapper/TravelPlanItemMapper.xml"),
                "<update id=\"promoteAlternativeContent\"", "</update>");

        assertThat(update)
                .contains("UPDATE travel_plan_items")
                .contains("SET content = #{content}")
                .contains("tag = #{tag}")
                // 작성자도 승격된 대안의 작성자로 바뀐다
                .contains("created_by_member_id = #{createdByMemberId}")
                .contains("version = version + 1")
                .contains("updated_at = CURRENT_TIMESTAMP")
                .contains("WHERE id = #{id}")
                .contains("AND travel_plan_day_id = #{travelPlanDayId}")
                .doesNotContain("${");
        // parent row 를 그대로 두므로 id 와 자리(display_order)는 건드리지 않는다
        assertThat(between(update, "SET", "WHERE"))
                .doesNotContain("display_order")
                .doesNotContain("condition_label");
    }

    private String mapperXml() throws IOException {
        return resource("/mapper/TravelPlanAlternativeMapper.xml");
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
