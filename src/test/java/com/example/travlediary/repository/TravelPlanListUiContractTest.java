package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 함께 계획하기 목록 화면.
 *
 * <p>진행 중인 여행과 완료된 여행이 한 화면에 위아래로 늘 함께 있다.
 * 쪽을 나누는 것은 계속 쌓이는 완료된 여행뿐이다.
 */
class TravelPlanListUiContractTest {

    // ── 두 구역 ─────────────────────────────────────────────

    @Test
    void bothListsLiveOnTheSameScreen() throws IOException {
        String list = listHtml();

        assertThat(list)
                .contains("진행 중인 여행")
                .contains("완료된 여행")
                .contains("th:each=\"plan : ${travelPlans}\"")
                .contains("th:each=\"plan : ${completedTravelPlans}\"");
        // 어느 쪽도 다른 쪽에 가려지지 않는다
        assertThat(list).doesNotContain("travel-plan-tabs").doesNotContain("status=");
    }

    @Test
    void eachSectionWearsItsOwnTotal() throws IOException {
        String list = listHtml();

        assertThat(list)
                .contains("th:text=\"${travelPlanPage.activeCount}\"")
                .contains("th:text=\"${travelPlanPage.completedCount}\"");
    }

    @Test
    void anEmptySectionKeepsItsPlaceWithOneShortLine() throws IOException {
        String list = listHtml();

        // 목록이 비어도 구역과 제목은 그대로 있고 한 줄만 적힌다
        assertThat(list)
                .contains("th:if=\"${#lists.isEmpty(travelPlans)}\"")
                .contains("아직 함께 계획 중인 여행이 없어요.")
                .contains("th:if=\"${#lists.isEmpty(completedTravelPlans)}\"")
                .contains("완료된 여행이 아직 없어요.");
    }

    // ── 쪽 이동 ─────────────────────────────────────────────

    @Test
    void onlyTheFinishedListHasAPager() throws IOException {
        String list = listHtml();

        assertThat(list).contains(
                "class=\"travel-plan-pagination\""
                        + " th:if=\"${travelPlanPage.completedTotalPages > 1}\"");
        // 쪽 이동은 완료된 여행 구역 안에만 있다
        assertThat(list.indexOf("id=\"travel-plan-completed-title\""))
                .isLessThan(list.indexOf("class=\"travel-plan-pagination\""));
    }

    @Test
    void thePagerCarriesOnlyTheCompletedPage() throws IOException {
        String pager = between(listHtml(), "class=\"travel-plan-pagination\"", "</nav>");

        assertThat(pager)
                .contains("@{/travel-plans(completedPage=${i})}")
                .contains("completedPage=${travelPlanPage.completedPage - 1}")
                .contains("completedPage=${travelPlanPage.completedPage + 1}");
        // 쪽을 옮긴 뒤 완료된 여행 구역으로 바로 돌아온다
        assertThat(pager).contains("'#travel-plan-completed'");
    }

    // ── 상단 / 카드 ─────────────────────────────────────────

    @Test
    void makingANewPlanIsASmallActionNextToTheTitle() throws IOException {
        String header = between(listHtml(), "class=\"travel-plan-list-header\"", "</header>");

        assertThat(header)
                .contains("함께 계획하기")
                .contains("친구들과 여행 일정을 함께 만들어 보세요.")
                .contains("+ 새 여행계획")
                .contains("th:href=\"@{/travel-plans/new}\"");
        assertThat(listHtml()).doesNotContain("새 공동 여행계획");
    }

    @Test
    void theNewPlanButtonStaysQuieterThanTheListItself() throws IOException {
        String button = between(resource("/static/css/travel-plan.css"),
                ".travel-plan-new-link {", "}");

        // 눌리는 자리는 남기되(32px) 나머지는 한 단계씩 낮춘다
        assertThat(button)
                .contains("min-height: 32px")
                .contains("padding: 4px 10px")
                .contains("font-size: 12px")
                .contains("font-weight: 600");
    }

    @Test
    void aCardStillLeadsWhereItAlwaysDid() throws IOException {
        String list = listHtml();

        // 진행 중은 편집방으로, 완료는 읽기 전용 최종본으로
        assertThat(list)
                .contains("@{|/travel-plans/${plan.travelPlanId}|}")
                .contains("@{|/travel-plans/${plan.travelPlanId}/final|}");
    }

    @Test
    void thereIsNoEmptyPictureFrameOnACard() throws IOException {
        String list = listHtml();

        // 대표 이미지 데이터가 없다. 빈 자리를 만들어 두지 않는다
        assertThat(list)
                .doesNotContain("travel-plan-card-thumb")
                .doesNotContain("representativeImageUrl")
                .doesNotContain("<img");
        assertThat(resource("/static/css/travel-plan.css"))
                .doesNotContain(".travel-plan-card-thumb");
    }

    @Test
    void aRunningCardShowsWhatIsWorthKnowingAtAGlance() throws IOException {
        String card = between(listHtml(), "class=\"travel-plan-card-link\"", "</li>");

        assertThat(card)
                .contains("${plan.title}")
                .contains("${plan.dayCount}")
                .contains("'참여 ' + ${plan.memberCount} + '/8'")
                // 내 자리는 둘 다 적되 약하게 둔다
                .contains("? 'OWNER' : 'MEMBER'")
                // 최근 활동은 있을 때만 자리를 차지한다
                .contains("th:if=\"${plan.lastActivityAt != null}\"")
                .contains("최근 활동 ");
    }

    @Test
    void theTwoSectionsAreSeparatedBySpaceRatherThanABox() throws IOException {
        String css = resource("/static/css/travel-plan.css");
        String gap = between(css, ".travel-plan-section + .travel-plan-section {", "}");

        assertThat(gap).contains("margin-top").contains("border-top: 1px solid");
        // 큰 상자나 그림자를 두지 않는다
        assertThat(between(css, ".travel-plan-card-link {", "}"))
                .doesNotContain("box-shadow")
                .contains("border-radius: 6px");
    }

    // ── 색 ──────────────────────────────────────────────────

    @Test
    void aCardIsNearlyWhitePaperRatherThanMoreBeige() throws IOException {
        String css = resource("/static/css/travel-plan.css");
        String card = between(css, ".travel-plan-card-link {", "}");

        // 따뜻한 바탕 위에 놓이는 카드만 중립으로 둔다
        assertThat(card)
                .contains("background: var(--tp-card-surface)")
                .contains("border: 1px solid var(--tp-card-line)");
        assertThat(between(css, "--tp-card-surface:", ";")).contains("#fdfdfd");
        // 갈색 선을 그대로 쓰지 않는다
        assertThat(card).doesNotContain("#e3dcd1").doesNotContain("#fffdf8");
    }

    @Test
    void aFinishedTripIsAResultNotGreyedOutData() throws IOException {
        String css = resource("/static/css/travel-plan.css");
        String completed = between(css, ".travel-plan-card-link.is-completed {", "}");

        /*
          끝난 여행도 사용자가 만들어 낸 결과물이다.
          흐리게 덮어 "지난 데이터" 로 보이게 하지 않는다.
          다른 것은 왼쪽 선의 색과 "완료" 표시뿐이다.
        */
        assertThat(between(css, ".travel-plan-card-link {", "}"))
                .contains("border-left: 3px solid var(--tp-live-line)")
                .contains("background: var(--tp-card-surface)");
        assertThat(completed).contains("border-left-color: var(--tp-card-line-strong)");
        // 종이도 글자도 진행 중인 것과 같다
        assertThat(completed)
                .doesNotContain("background:")
                .doesNotContain("opacity");
        assertThat(css).doesNotContain(
                ".travel-plan-card-link.is-completed .travel-plan-card-title");
    }

    @Test
    void theBadgesTellTheStateApartWithoutShouting() throws IOException {
        String css = resource("/static/css/travel-plan.css");

        // OWNER 일 때만 진행 중 색이 배고, MEMBER 는 중립이다
        assertThat(between(css, ".travel-plan-card-role {", "}"))
                .contains("color: var(--tp-card-ink-faint)");
        assertThat(between(css, ".travel-plan-card-role.is-owner {", "}"))
                .contains("color: var(--tp-live-accent)");
        // 완료 배지는 갈색도 파랑도 아닌 회색이고, 읽을 만큼의 대비는 있다
        assertThat(between(css, ".travel-plan-card-completed {", "}"))
                .contains("color: var(--tp-card-ink-soft)")
                .doesNotContain("--tp-poll-");
        assertThat(listHtml()).contains("? ' is-owner' : ''");
    }

    @Test
    void theWarmPageBackgroundIsLeftAlone() throws IOException {
        // 사이트 색을 바꾸는 것이 아니다. 바탕은 그대로 따뜻하다
        assertThat(between(resource("/static/css/travel-plan.css"), ":root {", "}"))
                .contains("--tp-page: #f5f2ec");
    }

    @Test
    void theEmptyStateStaysCompact() throws IOException {
        // 큰 점선 박스로 화면을 차지하지 않는다
        assertThat(between(resource("/static/css/travel-plan.css"), ".travel-plan-empty {", "}"))
                .contains("padding: 14px 12px");
    }

    private String listHtml() throws IOException {
        return resource("/templates/travelplan/list.html");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertThat(from).as("start marker %s", start).isNotNegative();
        int to = source.indexOf(end, from + start.length());
        assertThat(to).as("end marker %s", end).isNotNegative();
        return source.substring(from, to);
    }
}
