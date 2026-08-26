package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 완료된 여행 화면.
 *
 * <p>읽기 전용이다. 고칠 수 있는 것이 하나도 없어야 한다.
 * 완료된 여행은 최종본에서만 읽고 원본 방을 다시 들여다보지 않는다.
 */
class TravelPlanFinalUiContractTest {

    // ── 목록 ────────────────────────────────────────────────

    @Test
    void theListSeparatesFinishedTripsFromRunningOnes() throws IOException {
        String list = listHtml();

        assertThat(list)
                .contains("완료된 여행")
                .contains("completedTravelPlans")
                // 끝난 여행이라는 것을 한눈에 알 수 있게
                .contains("travel-plan-card-completed");
        // 한 건도 없으면 그 구역 자체를 두지 않는다
        assertThat(between(list, "class=\"travel-plan-completed-section\"", ">"))
                .contains("!#lists.isEmpty(completedTravelPlans)");
    }

    @Test
    void theFinishedListStaysShort() throws IOException {
        String card = between(listHtml(),
                "class=\"travel-plan-card-link is-completed\"", "</li>");

        // 제목 · 기간 · 참여 인원 · 완료 표시까지다
        assertThat(card)
                .contains("plan.title")
                .contains("plan.startDate")
                .contains("plan.endDate")
                .contains("plan.memberCount")
                .contains(">완료</span>");
        // 목록에서 일정까지 펼치지 않는다
        assertThat(card).doesNotContain("item").doesNotContain("day");
    }

    @Test
    void aFinishedCardLeadsToItsOwnReadOnlyPage() throws IOException {
        assertThat(listHtml())
                .contains("@{|/travel-plans/${plan.travelPlanId}/final|}");
    }

    // ── 상세 ────────────────────────────────────────────────

    @Test
    void theFinishedTripShowsWhatWasDecided() throws IOException {
        String detail = finalDetailHtml();

        assertThat(detail)
                .contains("finalPlan.snapshot.title")
                .contains("finalPlan.snapshot.startDate")
                .contains("finalPlan.members")
                .contains("finalPlan.days")
                .contains("finalPlan.itemsByDayId")
                .contains("finalPlan.alternativesByItemId");
        // 완성된 계획이라는 것이 드러난다
        assertThat(detail).contains("완성된 여행계획");
    }

    @Test
    void theAlternativesAreShownWhenTheSnapshotHasThem() throws IOException {
        String detail = finalDetailHtml();

        assertThat(detail)
                .contains("alt.alternativeOrder == 1 ? 'B' : 'C'")
                .contains("alt.conditionLabel")
                .contains("alt.content");
        // 편집 화면과 달리 접었다 펴는 버튼이 없다. 늘 그대로 보인다
        assertThat(detail).doesNotContain("data-travel-plan-alt-toggle");
    }

    @Test
    void nothingOnThePageCanBeEdited() throws IOException {
        String detail = finalDetailHtml();

        /*
          고칠 수 있는 것이 하나도 없어야 한다.
          폼도, 입력칸도, 추가 슬롯도, ⋯ 메뉴도 두지 않는다.
        */
        assertThat(detail)
                .doesNotContain("<form")
                .doesNotContain("<textarea")
                .doesNotContain("<input")
                .doesNotContain("<button")
                .doesNotContain("data-travel-plan-slot")
                .doesNotContain("data-travel-plan-item-form")
                .doesNotContain("data-travel-plan-menu-button");
    }

    @Test
    void nothingLiveIsAttachedToTheFinishedTrip() throws IOException {
        String detail = finalDetailHtml();

        // 채팅·투표·초대·실시간 편집은 끝난 여행에 없다
        assertThat(detail)
                .doesNotContain("data-travel-plan-chat")
                .doesNotContain("data-travel-plan-poll")
                .doesNotContain("data-travel-plan-invite")
                .doesNotContain("data-travel-plan-finalize")
                .doesNotContain("채팅")
                .doesNotContain("투표")
                .doesNotContain("초대");
        // 실시간 연결도 편집 스크립트도 싣지 않는다
        assertThat(detail)
                .doesNotContain("travel-plan-realtime.js")
                .doesNotContain("travel-plan-scheduler.js")
                .doesNotContain("travel-plan-chat.js")
                .doesNotContain("travel-plan-poll.js")
                .doesNotContain("stomp");
        // data-plan-id 가 없어 편집 스크립트가 붙을 자리도 없다
        assertThat(detail).doesNotContain("data-plan-id");
    }

    @Test
    void theFinishedTripIsReadFromTheFinalCopyAlone() throws IOException {
        String service = Files.readString(
                Path.of("src/main/java/com/example/travlediary/service/travelplan/"
                        + "TravelPlanFinalReadService.java"),
                StandardCharsets.UTF_8);

        // 원본 방을 다시 조합하지 않는다
        assertThat(service)
                .doesNotContain("TravelPlanItemMapper")
                .doesNotContain("TravelPlanAlternativeMapper")
                .doesNotContain("TravelPlanMapper travelPlanMapper");
        assertThat(service).contains("TravelPlanFinalMapper");
    }

    @Test
    void onlyThePeopleWhoWereThereCanOpenIt() throws IOException {
        String mapper = mapperXml();

        // 볼 자격은 조회 조건 안에 들어 있다
        assertThat(between(mapper, "<select id=\"findSnapshotByPlanAndUser\"", "</select>"))
                .contains("JOIN travel_plan_final_members m ON m.snapshot_id = s.id")
                .contains("m.user_id = #{userId}");
        // 목록도 마찬가지다
        assertThat(between(mapper, "<select id=\"findSnapshotsByUserId\"", "</select>"))
                .contains("m.user_id = #{userId}");
    }

    @Test
    void theOldEditingUrlLeadsToTheFinishedTrip() throws IOException {
        String controller = Files.readString(
                Path.of("src/main/java/com/example/travlediary/controller/travelplan/"
                        + "TravelPlanController.java"),
                StandardCharsets.UTF_8);

        // 완료된 여행에 함께했던 사람은 목록에서 다시 찾지 않아도 된다
        assertThat(controller)
                .contains("TravelPlanAccessNotice.COMPLETED_PARTICIPANT")
                .contains("\"redirect:/travel-plans/\" + travelPlanId + \"/final\"");
    }

    @Test
    void hidingOrDeletingAFinishedTripIsStillNotPartOfThis() throws IOException {
        assertThat(finalDetailHtml())
                .doesNotContain("숨기기")
                .doesNotContain("삭제");
        assertThat(listHtml()).doesNotContain("숨기기");
    }

    private String listHtml() throws IOException {
        return resource("/templates/travelplan/list.html");
    }

    private String finalDetailHtml() throws IOException {
        return resource("/templates/travelplan/final-detail.html");
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
