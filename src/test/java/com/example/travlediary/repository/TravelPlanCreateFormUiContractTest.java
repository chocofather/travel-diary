package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공동 여행계획 생성 폼 계약.
 * 검증은 서버가 최종 기준이고 HTML 제약은 보조다.
 */
class TravelPlanCreateFormUiContractTest {

    @Test
    void formPostsEveryFieldTheServiceNeeds() throws IOException {
        String create = createHtml();

        assertThat(create)
                .contains("th:object=\"${travelPlanCreateForm}\"")
                .contains("th:action=\"@{/travel-plans}\"")
                .contains("method=\"post\"")
                .contains("th:field=\"*{title}\"")
                .contains("th:field=\"*{startDate}\"")
                .contains("th:field=\"*{endDate}\"")
                .contains("th:field=\"*{displayName}\"")
                .contains("<button type=\"submit\"")
                .contains("공동 여행계획 만들기");
        // 이번 단계에 없는 입력은 만들지 않는다
        assertThat(create)
                .doesNotContain("representativeImage")
                .doesNotContain("enctype");
    }

    @Test
    void clientSideConstraintsMirrorTheServerRules() throws IOException {
        String create = createHtml();

        assertThat(create)
                .contains("maxlength=\"150\"")
                .contains("maxlength=\"50\"")
                .contains("type=\"date\"");
        // 네 입력 모두 required
        assertThat(countOf(create, "required")).isGreaterThanOrEqualTo(4);
    }

    @Test
    void everyValidatedFieldCanShowItsOwnErrorNextToTheInput() throws IOException {
        String create = createHtml();

        for (String field : new String[]{"title", "startDate", "endDate", "displayName"}) {
            assertThat(create).as("error slot for %s", field)
                    .contains("#fields.hasErrors('" + field + "')")
                    .contains("th:errors=\"*{" + field + "}\"");
        }
        // 전역 오류 박스가 필드 오류를 대체하지 않는다
        assertThat(create).contains("#fields.hasGlobalErrors()");
    }

    @Test
    void formReusesTheSiteLayoutAndShowsTheSuccessMessage() throws IOException {
        String create = createHtml();

        assertThat(create)
                .contains("~{layout/main :: layout(~{::body}, ~{::headFragment})}")
                .contains("/css/travel-plan.css")
                .contains("${travelPlanMessage}")
                .contains("이 여행계획 방에서 다른 참여자에게 표시되는 이름입니다.");
    }

    @Test
    void planCreationPostIsCsrfProtectedLikeTheOtherMemberFeatures() throws IOException {
        String securityConfig = Files.readString(
                Path.of("src/main/java/com/example/travlediary/config/SecurityConfig.java"),
                StandardCharsets.UTF_8);

        // /diaries POST 와 같은 방식으로 CSRF 매처에 등록한다
        assertThat(securityConfig)
                .contains("\"^/travel-plans$\", HttpMethod.POST.name()")
                .contains("\"^/diaries$\", HttpMethod.POST.name()");
        // 공동여행용 별도 인가 규칙은 추가하지 않는다 (anyRequest().authenticated() 사용)
        assertThat(securityConfig).doesNotContain("/travel-plans/**");
    }

    @Test
    void listShowsTitlePeriodMemberCountAndTheOwnerBadge() throws IOException {
        String list = resource("/templates/travelplan/list.html");

        assertThat(list)
                .contains("~{layout/main :: layout(~{::body}, ~{::headFragment})}")
                .contains("함께 계획하기")
                .contains("th:each=\"plan : ${travelPlans}\"")
                .contains("th:text=\"${plan.title}\"")
                .contains("${#temporals.format(plan.startDate, 'yyyy.MM.dd')}")
                .contains("${#temporals.format(plan.endDate, 'yyyy.MM.dd')}")
                .contains("${plan.dayCount}")
                .contains("'참여 ' + ${plan.memberCount} + '/8'")
                .contains("plan.role.name() == 'OWNER'")
                .contains("th:href=\"@{|/travel-plans/${plan.travelPlanId}|}\"")
                .contains("th:href=\"@{/travel-plans/new}\"");

        // 대표 이미지가 없으면 깨진 img 대신 단색 자리를 쓴다
        assertThat(list)
                .contains("th:if=\"${#strings.isEmpty(plan.representativeImageUrl)}\"")
                .contains("th:unless=\"${#strings.isEmpty(plan.representativeImageUrl)}\"");

        // 빈 상태
        assertThat(list)
                .contains("${#lists.isEmpty(travelPlans)}")
                .contains("아직 함께 계획 중인 여행이 없어요.")
                .contains("새로운 여행계획을 만들어 보세요.");
    }

    @Test
    void thePlannerShowsThePlanHeaderAndEveryDay() throws IOException {
        String detail = resource("/templates/travelplan/detail.html");

        assertThat(detail)
                .contains("~{layout/main :: layout(~{::body}, ~{::headFragment})}")
                .contains("th:text=\"${travelPlan.plan.title}\"")
                .contains("${#temporals.format(travelPlan.plan.startDate, 'yyyy.MM.dd')}")
                .contains("th:each=\"day : ${travelPlan.days}\"")
                .contains("'DAY ' + ${day.dayNumber}")
                .contains("${#temporals.format(day.planDate, 'M월 d일')}")
                .contains("${travelPlanMessage}")
                .contains("th:href=\"@{/travel-plans}\"");
    }

    @Test
    void thePlannerDoesNotSendTheUserToTheDayScreen() throws IOException {
        String detail = resource("/templates/travelplan/detail.html");

        // PC 메인 화면에서는 DAY 상세로 넘어가는 기본 동선을 두지 않는다
        assertThat(detail).doesNotContain("/days/${day.id}|}\"")
                .doesNotContain("travel-plan-day-link");
        // DAY 안에서 바로 편집한다
        assertThat(detail).contains("data-travel-plan-slot");
    }

    @Test
    void nothingIsOpenForTypingUntilASlotIsClicked() throws IOException {
        String detail = resource("/templates/travelplan/detail.html");

        // 슬롯 폼은 기본적으로 hidden 이고, 저장에 실패한 자리에서만 열려 온다
        assertThat(detail)
                .contains("th:hidden=\"${!dayOpen}\"")
                .contains("dayOpen=${openDayId != null and openDayId == day.id}");
        // 항상 떠 있는 추가/취소 버튼은 없다
        assertThat(detail)
                .doesNotContain(">추가</button>")
                .doesNotContain(">취소</button>")
                .doesNotContain("data-travel-plan-add-toggle");
        // 화면에 남는 유일한 폼은 슬롯 인라인 입력뿐이다
        assertThat(countOf(detail, "<form")).isEqualTo(1);
        assertThat(countOf(detail, "<textarea")).isEqualTo(1);
        assertThat(detail).doesNotContain("modal").doesNotContain("dialog");
    }

    @Test
    void onlySavedItemsBecomeNumberedLinesAndEachDayHasOneAddSlot() throws IOException {
        String detail = resource("/templates/travelplan/detail.html");

        // 번호가 붙은 줄은 저장된 일정에서만 만들어진다
        assertThat(detail)
                .contains("th:each=\"item, status : ${dayItems}\"")
                .contains("class=\"travel-plan-line is-item\"")
                .contains("${#numbers.formatInteger(status.count, 2)}");

        // 빈 줄을 미리 만들던 반복은 사라졌다
        assertThat(detail)
                .doesNotContain("#numbers.sequence")
                .doesNotContain("th:each=\"offset");

        // 추가 슬롯은 DAY 당 정확히 하나이고 번호가 없다
        assertThat(countOf(detail, "class=\"travel-plan-line is-slot\"")).isEqualTo(1);
        assertThat(countOf(detail, "data-travel-plan-slot>")).isEqualTo(1);
        assertThat(detail).contains(
                "<span class=\"travel-plan-line-order\" aria-hidden=\"true\"></span>");
    }

    @Test
    void theAddSlotIsQuietAndSharpensOnHover() throws IOException {
        String detail = resource("/templates/travelplan/detail.html");
        String css = resource("/static/css/travel-plan.css");

        assertThat(detail)
                .contains("class=\"travel-plan-slot-hint\"")
                .contains(">+ 일정 추가</span>");
        assertThat(css)
                .contains(".travel-plan-line.is-slot:hover .travel-plan-slot-hint")
                // 슬롯 번호 자리는 비워 둔다
                .contains(".travel-plan-line.is-slot .travel-plan-line-order");
    }

    @Test
    void thePlannerSitsOnItsOwnPaperAboveATintedSurface() throws IOException {
        String detail = resource("/templates/travelplan/detail.html");
        String css = resource("/static/css/travel-plan.css");

        assertThat(detail)
                .contains("class=\"travel-plan-page\"")
                .contains("class=\"travel-plan-paper\"");

        // 바깥 바탕과 종이 색이 서로 다르다
        String page = between(css, ".travel-plan-page {", "}");
        String paper = between(css, ".travel-plan-paper {", "}");
        assertThat(page).contains("background: #f5f2ec");
        assertThat(paper)
                .contains("background: #fffdf8")
                .contains("border: 1px solid")
                .contains("box-shadow")
                .contains("max-width: 900px");
        // 둥근 카드처럼 보이지 않게 한다
        assertThat(paper).contains("border-radius: 3px");
    }

    @Test
    void eachDayItemAndSlotCarriesAnIdentifierForLaterLiveEditing() throws IOException {
        String detail = resource("/templates/travelplan/detail.html");

        assertThat(detail)
                .contains("data-plan-id=${travelPlan.plan.id}")
                .contains("th:id=\"'day-' + ${day.id}\"")
                .contains("data-day-id=${day.id}")
                .contains("data-item-id=${item.id}")
                .contains("data-display-order=${item.displayOrder}")
                .contains("data-slot-index=${itemCount + 1}");
    }

    @Test
    void theSchedulerScriptKeepsOneEditorAndHandlesTheEditingKeys() throws IOException {
        String script = resource("/static/js/travel-plan-scheduler.js");

        // 동시에 열리는 입력칸은 하나뿐이다
        assertThat(script)
                .contains("let activeSlot = null")
                .contains("closeActive()")
                .contains("if (activeSlot === slot) return");
        // Enter 저장 / Shift+Enter 줄바꿈 / Esc 취소 / focus-out 저장
        assertThat(script)
                .contains("event.key === \"Enter\" && !event.shiftKey")
                .contains("event.key === \"Escape\"")
                .contains("\"blur\"")
                // 공백만 있으면 저장하지 않는다
                .contains("textarea.value.trim() === \"\"")
                .contains("form.requestSubmit()");
        // 아직 실시간 통신은 없다
        assertThat(script)
                .doesNotContain("WebSocket")
                .doesNotContain("SockJS")
                .doesNotContain("setInterval")
                .doesNotContain("fetch(")
                .doesNotContain("XMLHttpRequest");
    }

    @Test
    void thePlannerHasNoActionsFromLaterStages() throws IOException {
        String detail = resource("/templates/travelplan/detail.html");

        for (String notYet : new String[]{
                "수정", "삭제", "초대", "멤버 관리", "방 설정", "최종 확정", "채팅", "투표",
                "Plan B", "Plan C", "태그"}) {
            assertThat(detail).as("아직 없는 기능: %s", notYet).doesNotContain(notYet);
        }
    }

    @Test
    void eachDayShowsItsOwnItemsInOrder() throws IOException {
        String detail = resource("/templates/travelplan/detail.html");

        // DAY 별 목록을 그 DAY 의 id 로만 꺼내 서로 섞이지 않는다
        assertThat(detail)
                .contains("dayItems=${travelPlan.itemsByDayId.get(day.id)}")
                .contains("th:each=\"item, status : ${dayItems}\"")
                .contains("th:text=\"${item.content}\"")
                .contains("${#numbers.formatInteger(status.count, 2)}");
    }

    @Test
    void itemLinesKeepTheirLineBreaks() throws IOException {
        String css = resource("/static/css/travel-plan.css");

        assertThat(between(css, ".travel-plan-line-content {", "}"))
                .contains("white-space: pre-line");
    }


    @Test
    void thePlanPageReadsEveryDaysItemsInOneQuery() throws IOException {
        String mapper = resource("/mapper/TravelPlanItemMapper.xml");
        String select = between(mapper, "<select id=\"findByPlanId\"", "</select>");

        // DAY 수만큼 조회가 나가지 않도록 방 단위로 한 번에 읽는다
        assertThat(select)
                .contains("FROM travel_plan_items i")
                .contains("JOIN travel_plan_days d ON d.id = i.travel_plan_day_id")
                .contains("WHERE d.travel_plan_id = #{travelPlanId}")
                .contains("ORDER BY d.day_number ASC, i.display_order ASC, i.id ASC")
                .doesNotContain("${");
    }

    @Test
    void theDayPageListsItemsAndOffersASingleFreeTextForm() throws IOException {
        String day = resource("/templates/travelplan/day-detail.html");

        assertThat(day)
                .contains("~{layout/main :: layout(~{::body}, ~{::headFragment})}")
                .contains("th:object=\"${travelPlanDay}\"")
                // DAY 제목 / 날짜 / 방으로 돌아가기
                .contains("'DAY ' + *{day.dayNumber}")
                .contains("${#temporals.format(travelPlanDay.day.planDate, 'M월 d일')}")
                .contains("th:href=\"@{|/travel-plans/${travelPlanDay.plan.id}|}\"")
                // 기존 일정 반복 + 순번
                .contains("th:each=\"item, status : *{items}\"")
                .contains("th:text=\"${item.content}\"")
                .contains("${#numbers.formatInteger(status.count, 2)}")
                // 일정이 없을 때
                .contains("${#lists.isEmpty(travelPlanDay.items)}")
                .contains("아직 등록된 일정이 없습니다.")
                // 자유 텍스트 추가 폼
                .contains("th:object=\"${travelPlanItemCreateForm}\"")
                .contains("<textarea")
                .contains("th:field=\"*{content}\"")
                // 저장 후 새 GET 에서 브라우저가 직전 입력을 되살리지 않게 한다
                .contains("autocomplete=\"off\"")
                // th:field 가 id 를 필드명으로 바꾸므로 label 도 같은 값을 가리켜야 한다
                .contains("<label class=\"travel-plan-item-form-label\" for=\"content\">")
                .contains("/days/${travelPlanDay.day.id}/items|}")
                .contains("#fields.hasErrors('content')")
                .contains(">일정 추가</button>");
    }

    @Test
    void multilineItemContentKeepsItsLineBreaks() throws IOException {
        String css = resource("/static/css/travel-plan.css");

        assertThat(between(css, ".travel-plan-item-content {", "}"))
                .contains("white-space: pre-line");
    }

    @Test
    void theDayPageHasNoActionsFromLaterStages() throws IOException {
        String day = resource("/templates/travelplan/day-detail.html");

        for (String notYet : new String[]{
                "수정", "삭제", "순서", "Plan B", "Plan C", "투표", "채팅", "초대", "최종 확정", "태그"}) {
            assertThat(day).as("아직 없는 기능: %s", notYet).doesNotContain(notYet);
        }
        // 이번 단계의 폼은 일정 추가 하나뿐이다
        assertThat(countOf(day, "<form")).isEqualTo(1);
        assertThat(countOf(day, "<button")).isEqualTo(1);
    }

    @Test
    void theItemPostIsCsrfProtected() throws IOException {
        String securityConfig = Files.readString(
                Path.of("src/main/java/com/example/travlediary/config/SecurityConfig.java"),
                StandardCharsets.UTF_8);

        assertThat(securityConfig).contains(
                "\"^/travel-plans/[0-9]+/days/[0-9]+/items$\", HttpMethod.POST.name()");
    }

    @Test
    void headerLinksToTheTravelPlanListNextToTheDiaryMenu() throws IOException {
        String header = resource("/templates/fragments/header.html");

        assertThat(header).contains("<a href=\"/travel-plans\">함께 계획하기</a>");
        // 여행기록 메뉴 그룹 안, 나의 여행일기와 랜덤 여행 사이에 둔다
        assertThat(header.indexOf("함께 계획하기"))
                .isGreaterThan(header.indexOf("나의 여행일기"))
                .isLessThan(header.indexOf("랜덤 여행"));
        // 기존 항목은 그대로 둔다
        assertThat(header)
                .contains("<a href=\"/diaries\">나의 여행일기</a>")
                .contains("<a href=\"/random-travel\">랜덤 여행</a>");
    }

    private String createHtml() throws IOException {
        return resource("/templates/travelplan/create.html");
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }

    private int countOf(String source, String token) {
        int count = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            count++;
            index = source.indexOf(token, index + token.length());
        }
        return count;
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
