package com.example.travlediary.repository;

import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.TravelPlanStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공동 여행계획 방 생성에 필요한 3개 INSERT 계약.
 * 컬럼명은 docs/db/travel_diary_schema_reference.md 의 실제 구조와 일치해야 한다.
 */
class TravelPlanMapperContractTest {

    @Test
    void everyDeclaredMapperMethodHasAStatement() throws IOException {
        String mapper = mapperXml();
        String mapperInterface = Files.readString(
                Path.of("src/main/java/com/example/travlediary/repository/travelplan/TravelPlanMapper.java"),
                StandardCharsets.UTF_8);

        assertThat(mapper).contains(
                "namespace=\"com.example.travlediary.repository.travelplan.TravelPlanMapper\"");
        for (String id : new String[]{"insertPlan", "insertMember", "insertDays"}) {
            assertThat(mapperInterface).as("interface declares %s", id).contains(id);
            assertThat(mapper).as("xml defines %s", id).contains("id=\"" + id + "\"");
        }
    }

    @Test
    void planInsertUsesTheSchemaColumnsAndReturnsTheGeneratedId() throws IOException {
        String insert = between(mapperXml(), "<insert id=\"insertPlan\"", "</insert>");

        // Service 가 생성된 id 로 member/day 를 넣어야 하므로 generated key 가 필수다
        assertThat(insert)
                .contains("useGeneratedKeys=\"true\"")
                .contains("keyProperty=\"id\"")
                .contains("INSERT INTO travel_plans")
                .contains("created_by_user_id, title, start_date, end_date")
                .contains("representative_image_url, status")
                .contains("#{createdByUserId}, #{title}, #{startDate}, #{endDate}")
                .contains("#{representativeImageUrl}, #{status}");
        // 기본값이 있는 컬럼은 DB 에 맡긴다
        assertThat(insert)
                .doesNotContain("last_activity_at")
                .doesNotContain("created_at")
                .doesNotContain("updated_at")
                .doesNotContain("finalized_at");
        assertThat(insert).doesNotContain("${");
    }

    @Test
    void memberInsertCarriesTheRoleAndStatusAsColumns() throws IOException {
        String insert = between(mapperXml(), "<insert id=\"insertMember\"", "</insert>");

        assertThat(insert)
                .contains("useGeneratedKeys=\"true\"")
                .contains("keyProperty=\"id\"")
                .contains("INSERT INTO travel_plan_members")
                .contains("travel_plan_id, user_id, display_name, role, status")
                .contains("#{travelPlanId}, #{userId}, #{displayName}, #{role}, #{status}");
        // OWNER/ACTIVE 를 SQL 에 박지 않고 enum 값으로 넘긴다
        assertThat(insert)
                .doesNotContain("'OWNER'")
                .doesNotContain("'ACTIVE'")
                .doesNotContain("rejoin_allowed")
                .doesNotContain("${");
    }

    @Test
    void dayInsertWritesEveryDayInOneStatement() throws IOException {
        String insert = between(mapperXml(), "<insert id=\"insertDays\"", "</insert>");

        assertThat(insert)
                .contains("INSERT INTO travel_plan_days (travel_plan_id, day_number, plan_date)")
                .contains("<foreach collection=\"days\" item=\"day\" separator=\",\">")
                .contains("(#{travelPlanId}, #{day.dayNumber}, #{day.planDate})")
                .doesNotContain("${");
    }

    @Test
    void insertColumnsExistInTheSchemaReference() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);

        String plans = between(schema, "CREATE TABLE `travel_plans`", ") ENGINE=InnoDB");
        for (String column : new String[]{
                "created_by_user_id", "title", "start_date", "end_date",
                "representative_image_url", "status"}) {
            assertThat(plans).as("travel_plans.%s", column).contains("`" + column + "`");
        }

        String members = between(schema, "CREATE TABLE `travel_plan_members`", ") ENGINE=InnoDB");
        for (String column : new String[]{
                "travel_plan_id", "user_id", "display_name", "role", "status"}) {
            assertThat(members).as("travel_plan_members.%s", column).contains("`" + column + "`");
        }

        String days = between(schema, "CREATE TABLE `travel_plan_days`", ") ENGINE=InnoDB");
        for (String column : new String[]{"travel_plan_id", "day_number", "plan_date"}) {
            assertThat(days).as("travel_plan_days.%s", column).contains("`" + column + "`");
        }
    }

    @Test
    void enumsMatchTheDefaultsRecordedInTheSchema() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);

        // DB 는 varchar + enum 이름 저장 방식이므로 이름이 DEFAULT 와 맞아야 한다
        assertThat(between(schema, "CREATE TABLE `travel_plans`", ") ENGINE=InnoDB"))
                .contains("`status` varchar(20) NOT NULL DEFAULT '" + TravelPlanStatus.ACTIVE + "'");
        String members = between(schema, "CREATE TABLE `travel_plan_members`", ") ENGINE=InnoDB");
        assertThat(members)
                .contains("`role` varchar(20) NOT NULL DEFAULT '" + TravelPlanRole.MEMBER + "'")
                .contains("`status` varchar(20) NOT NULL DEFAULT '"
                        + TravelPlanMemberStatus.ACTIVE + "'");
        assertThat(TravelPlanRole.values())
                .containsExactly(TravelPlanRole.OWNER, TravelPlanRole.MEMBER);
    }

    private String mapperXml() throws IOException {
        return resource("/mapper/TravelPlanMapper.xml");
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
