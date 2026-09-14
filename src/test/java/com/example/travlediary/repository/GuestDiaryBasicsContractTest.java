package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비회원 체험 여행일기의 기본정보 계약.
 *
 * <p>고치기 전에는 체험 여행일기를 제목/여행 기간 없이 만들 수 있었고, 화면은 비어 있는 제목을
 * "체험 여행일기" 라고 대신 보여 주었다. 그래서 로그인 후 가져오기에서야 비어 있던 것이 드러났다.
 * (서버는 회원 여행일기와 같은 기준으로 제목·여행 기간을 요구한다)
 *
 * <p>여기에서 고정하는 것은 세 가지다.
 * 만들 때 회원과 같은 기본정보를 받는다는 것, 없는 값을 화면이 지어내지 않는다는 것,
 * 그리고 덜 채워진 예전 체험 여행일기는 지우지 않고 채울 수 있다는 것.
 */
class GuestDiaryBasicsContractTest {

    /** 1) 2) 만들기 화면이 회원과 같은 기본정보를 받는다. 제목과 여행 기간 모두 필수다. */
    @Test
    void theGuestCreateScreenAsksForTheSameBasicsAsAMember() throws IOException {
        String template = resource("templates/diary/demo-new.html");

        assertThat(template)
                .contains("<label for=\"diary-title\">제목</label>")
                .contains("data-guest-title")
                .contains("<label for=\"diary-start-date\">여행 시작일</label>")
                .contains("data-guest-start-date")
                .contains("<label for=\"diary-end-date\">여행 종료일</label>")
                .contains("data-guest-end-date");
        // 회원 화면과 같은 상자/조각을 쓴다. 체험 전용 폼을 따로 만들지 않는다.
        assertThat(template)
                .contains("class=\"diary-form\"")
                .contains("class=\"diary-form-field\"")
                .contains("class=\"diary-period-inputs\"")
                .contains("class=\"diary-period-item\"")
                .contains("placeholder=\"여행일기 제목을 입력해 주세요.\"")
                .contains("min=\"1000-01-01\" max=\"9999-12-31\"");
        // form 이라 회원 화면과 같은 required 검사가 먼저 지나간다.
        assertThat(template).contains("<form class=\"diary-form\" data-guest-form>");
        assertThat(template.split("required", -1)).hasSizeGreaterThanOrEqualTo(4);

        // 회원 화면의 같은 자리도 그대로다.
        assertThat(resource("templates/diary/new.html"))
                .contains("th:action=\"@{/diaries}\"")
                .contains("placeholder=\"여행일기 제목을 입력해 주세요.\"")
                .contains("<label for=\"diary-start-date\">여행 시작일</label>");
    }

    /** 2) 화면이 제목을 대신 지어내지 않는다. 회원 화면처럼 빈 칸으로 시작한다. */
    @Test
    void noPlaceholderTitleIsEverWrittenIntoTheDiary() throws IOException {
        assertThat(resource("templates/diary/demo-new.html"))
                .doesNotContain("value=\"체험 여행일기\"");
        assertThat(script("guest-diary-new.js"))
                .doesNotContain("|| '체험 여행일기'")
                // 여행 기간도 오늘로 미리 채우지 않는다. (회원 화면에도 기본값이 없다)
                .doesNotContain("prefillPeriod")
                .doesNotContain("new Date()");
    }

    /** 1) 2) 3) 기본정보가 갖춰지지 않으면 저장소가 직접 거절한다. 화면 검사에만 기대지 않는다. */
    @Test
    void theStoreItselfRefusesADiaryWithoutTitleOrTravelDates() throws IOException {
        String store = store();

        assertThat(store)
                .contains("function basicsError(basics)")
                .contains("return \"여행일기 제목을 입력해 주세요.\";")
                .contains("return \"여행 시작일을 입력해 주세요.\";")
                .contains("return \"여행 종료일을 입력해 주세요.\";")
                // 종료일이 시작일보다 빠르면 회원 여행일기와 같은 이유로 거절한다.
                .contains("if (input.endDate < input.startDate) {")
                .contains("return \"여행 종료일이 시작일보다 빠릅니다.\";")
                .contains("INVALID_BASICS: \"INVALID_BASICS\"");
        // 만들기와 보완 둘 다 같은 규칙을 지난다.
        assertThat(section(store, "function createDraft(basics)", "function getDraft()"))
                .contains("const invalid = basicsError(input);")
                .contains("return {ok: false, reason: REASON.INVALID_BASICS, message: invalid};");
        assertThat(section(store, "function updateDraft(patch)", "function addPage(page)"))
                .contains("const invalid = editsBasics ? basicsError(next) : null;")
                .contains("return {ok: false, reason: REASON.INVALID_BASICS, message: invalid};");

        // 회원 다이어리의 판정 기준도 그대로다. 한쪽만 느슨해지지 않는다.
        assertThat(source("service/diary/DiaryServiceImpl.java"))
                .contains("if (startDate == null || endDate == null) {")
                .contains("if (endDate.isBefore(startDate)) {");
    }

    /** 3) 4) 5) 화면도 만들기 전에 같은 규칙을 본다. 어긋나면 다음 단계로 넘어가지 않는다. */
    @Test
    void theCreateScreenStopsBeforeTheCoverStepWhenTheBasicsAreWrong() throws IOException {
        String script = script("guest-diary-new.js");
        String save = section(script, "function save()", "function createDraft(entered)");

        assertThat(save)
                .contains("const invalid = store.basicsError(entered);")
                .contains("say(invalid);")
                .contains("return;");
        // 어긋난 값에서는 표지 단계로도 책장으로도 가지 않는다.
        assertThat(save).doesNotContain("location.href");
    }

    /** 4) 5) 7) 제공 표지든 직접 꾸미기든 사용자가 입력한 값이 그대로 draft 에 들어간다. */
    @Test
    void bothCoverChoicesStoreTheEnteredTitleAndPeriod() throws IOException {
        String create = section(script("guest-diary-new.js"),
                "function createDraft(entered)", "function completeDraft(entered)");

        assertThat(create)
                .contains("title: entered.title,")
                .contains("startDate: entered.startDate,")
                .contains("endDate: entered.endDate,")
                // 첫 장의 날짜도 지어내지 않고 입력받은 여행 시작일을 쓴다.
                .contains("store.addPage({pageDate: entered.startDate});")
                // 두 갈래 모두 이 자리에서 만들어진 뒤에 갈린다.
                .contains("? page.dataset.coverUrl")
                .contains(": page.dataset.shelfUrl");
    }

    /** 6) 8) 9) 덜 채워진 예전 체험 여행일기는 지우지 않고 기본정보만 채운다. */
    @Test
    void anIncompleteOlderDraftIsCompletedInsteadOfDiscarded() throws IOException {
        String script = script("guest-diary-new.js");

        assertThat(script)
                .contains("params.get('mode') === 'complete'")
                // 채운 뒤 돌아갈 자리 — 정해진 이름표(shelf / edit / import)로만 정해진다.
                .contains("page.dataset[RETURN_TARGETS[params.get('returnTo')] || 'shelfUrl']");

        String complete = section(script, "function completeDraft(entered)", "function say(message)");
        assertThat(complete)
                .contains("store.updateDraft({")
                .contains("title: entered.title,")
                .contains("startDate: entered.startDate,")
                .contains("endDate: entered.endDate,")
                // 표지와 페이지는 이 요청에 담지 않는다. 있던 것이 그대로 남는다.
                .doesNotContain("coverType:")
                .doesNotContain("coverStyle:")
                .doesNotContain("coverDesign:")
                .doesNotContain("pages:")
                .doesNotContain("store.createDraft")
                .doesNotContain("store.clear()");

        // 저장소도 기본정보 수정에서 페이지/표지 요소를 건드리지 않는다.
        assertThat(section(store(), "function updateDraft(patch)", "function addPage(page)"))
                .contains("pages: draft.pages")
                .contains("? draft.coverDesign : normalizeCoverDesign(input.coverDesign)");
    }

    /** 6) 10) 책장 카드는 실제 제목과 실제 기간만 보여 준다. 없는 날짜를 지어내지 않는다. */
    @Test
    void theShelfCardShowsTheRealTitleAndPeriod() throws IOException {
        String script = script("guest-diary-demo.js");

        assertThat(script)
                .contains("const complete = store.isComplete(draft);")
                .contains("show(incomplete, !complete);")
                // 회원 목록 카드와 같은 모양이다.
                .contains("draft.startDate + ' ~ ' + draft.endDate : '';")
                .doesNotContain("|| '체험 여행일기'");

        String template = resource("templates/diary/demo.html");
        assertThat(template)
                .contains("data-guest-incomplete")
                .contains("여행일기 정보가 완성되지 않았습니다.")
                .contains("th:href=\"@{/diaries/demo/new(mode=complete,returnTo=shelf)}\"")
                .doesNotContain("data-guest-book-title>체험 여행일기<");
        // 회원 목록과 같은 기간 표기를 쓴다.
        assertThat(resource("templates/diary/list.html"))
                .contains("th:text=\"|${diary.startDate} ~ ${diary.endDate}|\"");
    }

    /** 7) 9) 가져오기 화면은 지금 상태를 그대로 보여 준다. 없는 제목을 있는 것처럼 쓰지 않는다. */
    @Test
    void theImportScreenNeverDressesUpAnIncompleteDraft() throws IOException {
        String script = script("guest-diary-import.js");

        assertThat(script)
                .contains("const complete = found && store.isComplete(draft);")
                .contains("show(incomplete, found && !complete);")
                // 기본정보가 없으면 저장 버튼 자체를 내놓지 않는다.
                // (저장 버튼은 기본정보와 페이지 날짜가 모두 갖춰졌을 때만 나온다)
                .contains("show(actions, dated);")
                .contains("if (!store.isComplete(draft)) return;")
                .doesNotContain("|| '체험 여행일기'");

        assertThat(resource("templates/diary/import.html"))
                .contains("data-guest-import-incomplete")
                .contains("여행일기 정보가 완성되지 않았습니다.")
                .contains("th:href=\"@{/diaries/demo/new(mode=complete,returnTo=import)}\"")
                .doesNotContain("data-guest-import-title>체험 여행일기<");
    }

    /**
     * 11) 서버 검증은 그대로다.
     *
     * <p>화면을 고쳤다고 서버가 느슨해지지 않는다. 제목과 여행 기간은 여전히 필수이고,
     * 서버가 기본 제목이나 오늘 날짜를 대신 채워 넣지 않는다.
     */
    @Test
    void theServerSideImportValidationIsNotWeakened() throws IOException {
        String service = source("service/diary/GuestDiaryImportService.java");

        assertThat(service)
                .contains("diary.setTitle(requireTitle(manifest.title()));")
                .contains("throw badRequest(\"여행일기 제목을 입력해 주세요.\");")
                .contains("diary.setStartDate(requireDate(manifest.startDate(), \"여행 시작일\"));")
                .contains("diary.setEndDate(requireDate(manifest.endDate(), \"여행 종료일\"));")
                .contains("throw badRequest(label + \"을 입력해 주세요.\");")
                .contains("if (diary.getEndDate().isBefore(diary.getStartDate())) {");
        // 비어 있는 값을 서버가 대신 채우지 않는다. (기본 제목도, 오늘 날짜도 넣지 않는다)
        assertThat(service)
                .doesNotContain("\"체험 여행일기\"")
                .doesNotContain("LocalDate.now()");
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
