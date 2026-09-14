package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비회원 체험 여행일기에서 장(page)의 날짜 계약.
 *
 * <p>고치기 전에는 [새 페이지] 를 누르면 곧바로 {@code addPage({})} 로 장이 늘어나
 * 2·3번째 장의 날짜가 비어 있었다. 회원 페이지는 날짜를 고른 뒤에 만들어지고
 * (DiaryPageServiceImpl 이 날짜를 요구하고 여행 기간 안인지까지 본다)
 * 가져오기도 같은 기준이라, 그 장들은 저장할 때가 되어서야 거절당했다.
 *
 * <p>여기에서 고정하는 것은 세 가지다. 장은 날짜를 고른 뒤에 만들어진다는 것,
 * 그 날짜가 여행 기간 안이라는 것을 화면이 아니라 저장소가 지킨다는 것,
 * 그리고 날짜가 없던 예전 장은 지우지 않고 채울 수 있다는 것.
 */
class GuestDiaryPageDateContractTest {

    /** 1) 7) 첫 장은 묻지 않는다. 만들 때 입력받은 여행 시작일을 그대로 쓴다. */
    @Test
    void theFirstPageTakesTheTravelStartDateWithoutAsking() throws IOException {
        assertThat(script("guest-diary-new.js"))
                .contains("store.addPage({pageDate: entered.startDate});");
        // 마지막 장을 지워 한 장도 없을 때 다시 여는 장도 같은 규칙이다.
        assertThat(script("guest-diary-editor.js"))
                .contains("store.addPage({pageDate: draft.startDate});");
    }

    /** 2) 3) 두 번째부터는 날짜를 고른 뒤에 만들어진다. 누르자마자 늘어나지 않는다. */
    @Test
    void aNewPageIsCreatedOnlyAfterItsDateIsChosen() throws IOException {
        String editor = script("guest-diary-editor.js");

        // 예전처럼 날짜 없이 부르는 자리는 남아 있지 않다.
        assertThat(editor).doesNotContain("store.addPage({})");
        assertThat(editor)
                .contains("const chosen = page.querySelector('[data-guest-new-page-date]').value;")
                .contains("const result = store.addPage({pageDate: chosen});");

        // 회원 읽기 화면과 같은 팝오버 조각·같은 CSS 를 쓴다. 새 modal 을 만들지 않는다.
        String template = resource("templates/diary/demo-edit.html");
        assertThat(template)
                .contains("<details class=\"diary-page-add\" data-guest-page-add>")
                .contains("class=\"diary-page-add-button is-labelled\"")
                .contains("<span>페이지 날짜</span>")
                .contains("<input type=\"date\" name=\"pageDate\" required data-guest-new-page-date />")
                .contains("data-guest-action=\"confirm-add-page\"");
        assertThat(resource("templates/diary/detail.html"))
                .contains("<details class=\"diary-page-add\">")
                .contains("<input type=\"date\" name=\"pageDate\" required");
    }

    /** 5) 고르기 좋은 날짜를 미리 넣어 두되, 확인 없이 만들지는 않는다. */
    @Test
    void theSuggestedDateIsOnlyAStartingPoint() throws IOException {
        String suggest = section(script("guest-diary-editor.js"),
                "function suggestedPageDate()", "function dayAfter(date)");

        // 다음 날 → 여행 기간을 넘으면 보고 있는 장의 날짜 → 그것도 없으면 여행 시작일
        assertThat(suggest)
                .contains("const nextDay = current.pageDate ? dayAfter(current.pageDate) : null;")
                .contains("if (nextDay && draft.endDate && nextDay <= draft.endDate) {")
                .contains("return current.pageDate || draft.startDate || '';")
                // 미리 넣어 두기만 한다. 여기에서 장을 만들지 않는다.
                .doesNotContain("addPage");
    }

    /** 3) 6) 여행 기간 밖의 날짜는 저장소가 거부한다. 화면의 min/max 에만 기대지 않는다. */
    @Test
    void theStoreItselfRefusesAPageDateOutsideTheTravelPeriod() throws IOException {
        String store = store();

        assertThat(store)
                .contains("function pageDateError(draft, pageDate)")
                .contains("return \"페이지 날짜를 선택해 주세요.\";")
                .contains("if (pageDate < draft.startDate || pageDate > draft.endDate) {")
                .contains("INVALID_PAGE_DATE: \"INVALID_PAGE_DATE\"")
                // 형식과 실제 있는 날인지는 기본정보 날짜와 같은 규칙으로 본다.
                .contains("if (!isDateValue(pageDate)) {");

        String addPage = section(store, "function addPage(page)", "function getPage(pageId)");
        assertThat(addPage)
                .contains("const invalid = pageDateError(draft, isObject(page) ? page.pageDate : null);")
                .contains("return {ok: false, reason: REASON.INVALID_PAGE_DATE, message: invalid};")
                // 3장 한도는 그대로다. 한도를 먼저 보고 날짜를 본다.
                .contains("if (draft.pages.length >= MAX_PAGES) {")
                .contains("return {ok: false, reason: REASON.PAGE_LIMIT_REACHED, limit: MAX_PAGES};");

        // 화면에도 같은 범위를 알려 준다. (고르기 편하라고 두는 값이다)
        assertThat(script("guest-diary-editor.js"))
                .contains("if (draft.startDate) input.min = draft.startDate;")
                .contains("if (draft.endDate) input.max = draft.endDate;");
    }

    /** 4) 같은 날짜에 여러 장은 회원과 마찬가지로 막지 않는다. 자리번호와 날짜는 다른 것이다. */
    @Test
    void twoPagesMayShareTheSameDate() throws IOException {
        String addPage = section(store(), "function addPage(page)", "function getPage(pageId)");

        // 이미 쓰인 날짜인지 세지 않는다.
        assertThat(addPage)
                .doesNotContain("some((item) => item.pageDate")
                .doesNotContain("DUPLICATE");
        // 자리번호는 날짜와 무관하게 만들어진 차례대로 붙는다.
        assertThat(addPage).contains("normalizePage(page, draft.pages.length + 1)");
        // 회원 서비스도 같은 입장이다.
        assertThat(source("service/diary/DiaryPageServiceImpl.java"))
                .contains("// 같은 날짜에 여러 페이지는 허용하되, 여행 기간을 벗어난 날짜는 막는다.");
    }

    /** 8) 9) 만들어진 장을 바로 연다. 기존 흐름(currentPageId → 다시 열기)을 그대로 쓴다. */
    @Test
    void theNewPageBecomesTheOpenPage() throws IOException {
        String editor = script("guest-diary-editor.js");

        assertThat(section(editor, "action('confirm-add-page')", "action('set-page-date')"))
                .contains("openPage(result.page.pageId);");
        assertThat(section(editor, "function openPage(pageId)", "function wireActions()"))
                .contains("store.setCurrentPageId(pageId);")
                .contains("global.location.reload();");
    }

    /** 10) 3장을 다 썼으면 날짜부터 묻지 않고 기존 로그인 유도를 그대로 보여 준다. */
    @Test
    void theFourthPageStillLeadsToTheExistingLoginPrompt() throws IOException {
        String editor = script("guest-diary-editor.js");
        String add = section(editor,
                "action('add-page').addEventListener", "action('confirm-add-page')");

        assertThat(add)
                .contains("if (draft.pages.length >= store.MAX_PAGES) {")
                .contains("event.preventDefault();")
                .contains("openPrompt();");
        // 저장소가 거절한 경우에도 같은 안내로 이어진다.
        assertThat(editor).contains("if (result.reason === store.REASON.PAGE_LIMIT_REACHED) {");
        assertThat(resource("templates/diary/demo-edit.html"))
                .contains("data-guest-prompt")
                .contains("비회원 체험에서는 최대 3페이지까지 작성할 수 있어요");
    }

    /** 11) 12) 날짜가 없던 예전 장은 지우지 않는다. 그 장의 날짜만 채운다. */
    @Test
    void anUndatedOlderPageIsCompletedInsteadOfDiscarded() throws IOException {
        String editor = script("guest-diary-editor.js");

        assertThat(store())
                .contains("function pagesMissingDate(draft)")
                .contains("return draft.pages.filter((page) => !isDateValue(page.pageDate));");
        assertThat(editor)
                .contains("const undated = store.pagesMissingDate(store.getDraft())")
                // 여행 기간이 갖춰진 뒤에 이 장의 날짜를 묻는다.
                .contains("show(page.querySelector('[data-guest-page-date-missing]'), ready && undated);")
                // 안내에서 페이지 설정의 날짜 칸으로 데려간다.
                .contains("action('set-page-date')");

        String template = resource("templates/diary/demo-edit.html");
        assertThat(template)
                .contains("data-guest-page-date-missing")
                .contains("이 페이지의 날짜를 선택해 주세요.")
                .contains("data-guest-action=\"set-page-date\"");

        /*
          날짜만 고친다. 본문·꾸미기·사진은 이 요청에 담기지 않아 그대로 남는다.
          (저장소도 페이지 배열의 나머지 값을 그대로 두고 합친다)
        */
        String save = section(editor,
                "action('save-page-settings').addEventListener", "action('login')");
        assertThat(save)
                .contains("patch.pageDate = page.querySelector('[data-guest-page-date]').value || null;")
                .doesNotContain("elements")
                .doesNotContain("photoRef")
                .doesNotContain("removePage");
        assertThat(section(store(), "function updatePage(pageId, patch)", "function removePage(pageId)"))
                .contains("Object.assign({}, draft.pages[index], isObject(patch) ? patch : {})")
                // 날짜를 담은 요청만 날짜 규칙을 다시 본다. (본문 자동 저장은 그대로 지나간다)
                .contains("if (input.pageDate !== undefined) {");
    }

    /** 13) 가져오기 화면이 먼저 알려 준다. 보내 놓고 거절당하지 않는다. */
    @Test
    void theImportScreenDetectsUndatedPagesBeforeSending() throws IOException {
        String script = script("guest-diary-import.js");

        assertThat(script)
                .contains("const dated = complete && store.pagesMissingDate(draft).length === 0;")
                .contains("show(undated, complete && !dated);")
                .contains("show(actions, dated);")
                // 보내기 직전에도 한 번 더 막는다.
                .contains("if (store.pagesMissingDate(draft).length > 0) return;");

        assertThat(resource("templates/diary/import.html"))
                .contains("data-guest-import-undated")
                .contains("날짜가 설정되지 않은 페이지가 있습니다.")
                .contains("th:href=\"@{/diaries/demo/edit}\"");
    }

    /**
     * 14) 15) 서버와 회원 흐름은 그대로다.
     *
     * <p>화면이 미리 봐 준다고 서버가 느슨해지지 않는다. 페이지 날짜는 여전히 필수이고,
     * 서버가 여행 시작일이나 오늘 날짜를 대신 넣지 않는다.
     */
    @Test
    void theServerAndTheMemberFlowAreUnchanged() throws IOException {
        assertThat(source("service/diary/GuestDiaryImportService.java"))
                .contains("prepared.setPageDate(requireDate(page.pageDate(), \"페이지 날짜\"));")
                .contains("throw badRequest(label + \"을 입력해 주세요.\");")
                .doesNotContain("LocalDate.now()");
        assertThat(source("service/diary/DiaryPageServiceImpl.java"))
                .contains("throw new ResponseStatusException(HttpStatus.BAD_REQUEST, \"페이지 날짜를 입력해 주세요.\");")
                .contains("if (pageDate.isBefore(diary.getStartDate()) || pageDate.isAfter(diary.getEndDate())) {");
        // 회원 페이지 추가 자리도 그대로다.
        assertThat(source("controller/diary/DiaryController.java"))
                .contains("@PostMapping(\"/{diaryId:\\\\d+}/pages\")")
                .contains("page.setPageDate(pageForm.getPageDate());");
    }

    private String script(String name) throws IOException {
        return resource("static/js/" + name);
    }

    private String store() throws IOException {
        return script("guest-diary-draft-store.js");
    }

    /** 파일의 한 토막. 시작 표시부터 다음 표시 직전까지만 본다. */
    private String section(String content, String from, String until) {
        int start = content.indexOf(from);
        int end = content.indexOf(until, start + 1);
        assertThat(start).as("시작 표시를 찾지 못했습니다: " + from).isNotNegative();
        assertThat(end).as("끝 표시를 찾지 못했습니다: " + until).isGreaterThan(start);
        return content.substring(start, end);
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relativePath),
                StandardCharsets.UTF_8);
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/example/travlediary").resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
