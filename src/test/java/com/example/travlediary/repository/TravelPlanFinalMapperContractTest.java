package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 완료된 여행 계획의 최종본 계약.
 * 컬럼명은 docs/db/travel_diary_schema_reference.md 의 실제 구조와 일치해야 한다.
 * 이번 단계에서 표를 새로 만들거나 컬럼을 더하지 않는다.
 */
class TravelPlanFinalMapperContractTest {

    @Test
    void everyDeclaredMapperMethodHasAStatement() throws IOException {
        String mapper = mapperXml();
        String mapperInterface = Files.readString(
                Path.of("src/main/java/com/example/travlediary/repository/travelplan/"
                        + "TravelPlanFinalMapper.java"),
                StandardCharsets.UTF_8);

        assertThat(mapper).contains("namespace=\"com.example.travlediary.repository.travelplan."
                + "TravelPlanFinalMapper\"");
        for (String id : new String[]{
                "insertSnapshot", "insertMember", "insertDay", "insertItem",
                "insertAlternative", "existsByPlanId", "existsMemberByPlanAndUser",
                "findSnapshotsByUserId", "findSnapshotByPlanAndUser",
                "findMembersBySnapshotId", "findDaysBySnapshotId",
                "findItemsBySnapshotId", "findAlternativesBySnapshotId"}) {
            assertThat(mapperInterface).as("interface declares %s", id).contains(id);
            assertThat(mapper).as("xml defines %s", id).contains("id=\"" + id + "\"");
        }
    }

    @Test
    void everyFinalColumnExistsInTheSchemaReference() throws IOException {
        String schema = schemaReference();

        assertThat(between(schema,
                "CREATE TABLE `travel_plan_final_snapshots`", ") ENGINE=InnoDB"))
                .contains("`travel_plan_id`")
                .contains("`title`")
                .contains("`start_date`")
                .contains("`end_date`")
                .contains("`representative_image_url`")
                .contains("`finalized_at`");
        assertThat(between(schema,
                "CREATE TABLE `travel_plan_final_members`", ") ENGINE=InnoDB"))
                .contains("`snapshot_id`")
                .contains("`user_id`")
                .contains("`display_name`")
                .contains("`role`");
        assertThat(between(schema,
                "CREATE TABLE `travel_plan_final_days`", ") ENGINE=InnoDB"))
                .contains("`snapshot_id`")
                .contains("`day_number`")
                .contains("`plan_date`");
        assertThat(between(schema,
                "CREATE TABLE `travel_plan_final_items`", ") ENGINE=InnoDB"))
                .contains("`final_day_id`")
                .contains("`content`")
                .contains("`tag`")
                .contains("`display_order`");
        assertThat(between(schema,
                "CREATE TABLE `travel_plan_final_item_alternatives`", ") ENGINE=InnoDB"))
                .contains("`final_item_id`")
                .contains("`alternative_order`")
                .contains("`condition_label`")
                .contains("`content`");
    }

    @Test
    void aRoomKeepsExactlyOneFinalCopy() throws IOException {
        assertThat(between(schemaReference(),
                "CREATE TABLE `travel_plan_final_snapshots`", ") ENGINE=InnoDB"))
                .contains("UNIQUE KEY `uk_travel_plan_final_snapshots_plan` (`travel_plan_id`)");
    }

    @Test
    void theFinalCopyIsTiedTogetherByItsOwnNumbers() throws IOException {
        String mapper = mapperXml();

        /*
          원본 표의 번호를 그대로 쓰지 않는다.
          최종본 안에서만 통하는 번호로 새로 이어, 원본이 나중에 어떻게 되든 그대로 남는다.
        */
        assertThat(between(mapper, "<insert id=\"insertDay\"", "</insert>"))
                .contains("snapshot_id")
                .doesNotContain("travel_plan_day_id");
        assertThat(between(mapper, "<insert id=\"insertItem\"", "</insert>"))
                .contains("final_day_id")
                .doesNotContain("travel_plan_item_id");
        assertThat(between(mapper, "<insert id=\"insertAlternative\"", "</insert>"))
                .contains("final_item_id")
                .doesNotContain("travel_plan_item_id");

        // 붙일 자리를 알려면 방금 만든 번호를 받아야 한다
        for (String id : new String[]{"insertSnapshot", "insertDay", "insertItem"}) {
            assertThat(between(mapper, "<insert id=\"" + id + "\"", ">"))
                    .as("%s", id)
                    .contains("useGeneratedKeys=\"true\"")
                    .contains("keyProperty=\"id\"");
        }
    }

    @Test
    void theFinalCopyOnlyTouchesTheFinalTables() throws IOException {
        String mapper = mapperXml();

        assertThat(mapper)
                .contains("travel_plan_final_snapshots")
                .contains("travel_plan_final_members")
                .contains("travel_plan_final_days")
                .contains("travel_plan_final_items")
                .contains("travel_plan_final_item_alternatives");
        // 투표와 대화는 최종본에 옮겨 적지 않는다
        assertThat(mapper)
                .doesNotContain("travel_plan_polls")
                .doesNotContain("travel_plan_chat_messages")
                // 원본 일정을 다시 조합하지 않는다. 최종본이 기준이다
                .doesNotContain("travel_plan_items")
                .doesNotContain("travel_plan_item_alternatives")
                .doesNotContain("travel_plan_days")
                // 최종본은 한번 만들어지면 바뀌지 않는다. 고치거나 지우지 않는다
                .doesNotContain("UPDATE ")
                .doesNotContain("DELETE ");
    }

    @Test
    void whoeverIsCopiedIntoTheFinalCopyKeepsTheirAccount() throws IOException {
        String planMapper = resource("/mapper/TravelPlanMapper.xml");

        /*
          최종 명단의 user_id 로 "내 완료된 여행" 을 찾는다.
          옮겨 적을 때 계정 번호를 읽지 않으면 NULL 로 저장되고,
          그러면 그 여행은 누구의 목록에도 나오지 않는다(NULL 은 무엇과도 같지 않다).
        */
        assertThat(between(planMapper,
                "<select id=\"findActiveMembersForSnapshot\"", "</select>"))
                .contains("user_id")
                .contains("status = #{memberStatus}");

        // 화면에 뿌리는 조회는 지금처럼 계정 번호를 읽지 않는다
        assertThat(between(planMapper,
                "<select id=\"findActiveMembersByPlanId\"", "</select>"))
                .doesNotContain("user_id");
    }

    @Test
    void theFinishedListIsFoundByTheAccountThatWasCopied() throws IOException {
        // 위에서 저장한 그 값으로 찾는다. 둘이 어긋나면 목록이 비어 보인다
        assertThat(between(mapperXml(), "<insert id=\"insertMember\"", "</insert>"))
                .contains("user_id");
        assertThat(between(mapperXml(), "<select id=\"findSnapshotsByUserId\"", "</select>"))
                .contains("m.user_id = #{userId}");
    }

    @Test
    void readingTheFinishedTripTakesTheSnapshotsOwnOrder() throws IOException {
        String mapper = mapperXml();

        // 완료 시점의 차례가 화면에 그대로 나온다
        assertThat(between(mapper, "<select id=\"findDaysBySnapshotId\"", "</select>"))
                .contains("ORDER BY day_number ASC");
        assertThat(between(mapper, "<select id=\"findItemsBySnapshotId\"", "</select>"))
                .contains("ORDER BY d.day_number ASC, i.display_order ASC, i.id ASC");
        assertThat(between(mapper, "<select id=\"findAlternativesBySnapshotId\"", "</select>"))
                .contains("ORDER BY a.final_item_id ASC, a.alternative_order ASC");
    }

    @Test
    void theListCountsWhoWasThereWithoutAskingAgain() throws IOException {
        // 목록에서 여행 수만큼 조회가 나가지 않게 한 문장으로 센다
        assertThat(between(mapperXml(), "<select id=\"findSnapshotsByUserId\"", "</select>"))
                .contains("SELECT COUNT(*)")
                .contains("AS member_count")
                .contains("ORDER BY s.finalized_at DESC");
    }

    private String schemaReference() throws IOException {
        return Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
    }

    private String mapperXml() throws IOException {
        return resource("/mapper/TravelPlanFinalMapper.xml");
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
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
