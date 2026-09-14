package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 체험 여행일기를 지우는 자리와, 덜 채워진 체험 여행일기를 편집 화면에서 채우는 길.
 *
 * <p>고치기 전에는 만들어 둔 체험 여행일기를 지울 방법이 [+ 새 여행일기] 의 교체뿐이었고,
 * 기본정보가 없는 예전 체험 여행일기로 편집 화면에 들어오면 "날짜를 선택해 주세요" 안내를 따라가도
 * "여행 기간을 먼저 입력해 주세요" 에서 막혀 어디로 가야 하는지 알 수 없었다.
 *
 * <p>여기에서 고정하는 것은 세 가지다. 지우는 자리가 따로 있다는 것(그리고 사진까지 함께 거둔다는 것),
 * 기본정보가 없으면 편집 화면이 그 사실과 갈 자리를 먼저 알린다는 것,
 * 그리고 돌아갈 자리는 주소가 아니라 정해진 이름표로만 정해진다는 것.
 */
class GuestDiaryDraftResetContractTest {

    /** 1) 2) 카드 ⋯ 메뉴에 삭제가 있고, 누른다고 바로 지우지 않는다. */
    @Test
    void theShelfMenuOffersADeleteThatAsksFirst() throws IOException {
        String template = resource("templates/diary/demo.html");

        // 회원 목록의 삭제 칸과 같은 조각이다. (지우는 자리만 다르다)
        assertThat(template)
                .contains("class=\"diary-book-menu-item is-danger\"")
                .contains("data-guest-action=\"delete-diary\"")
                .contains("체험 여행일기 삭제")
                // 확인 판. 표지 수정 칸은 그대로 남는다.
                .contains("data-guest-delete-prompt")
                .contains("체험 여행일기를 삭제할까요?")
                .contains("작성한 페이지와 사진도 함께 삭제됩니다.")
                .contains("data-guest-action=\"confirm-delete\"")
                .contains("data-guest-action=\"cancel-delete\"")
                .contains("th:href=\"@{/diaries/demo/cover}\">표지 수정</a>");
        assertThat(resource("templates/diary/list.html"))
                .contains("class=\"diary-book-menu-item is-danger\"");

        // 메뉴를 누르면 묻기만 한다. 지우는 일은 확인 버튼에만 달려 있다.
        String script = script("guest-diary-demo.js");
        assertThat(section(script,
                "page.querySelector('[data-guest-action=\"delete-diary\"]')",
                "deletePrompt?.querySelector('[data-guest-action=\"cancel-delete\"]')"))
                .contains("show(deletePrompt, true)")
                .doesNotContain("discardDraft");
    }

    /** 2) 3) 4) 10) 삭제를 확정하면 사진 → draft → 쪽지 차례로 거두고 빈 상태로 돌아간다. */
    @Test
    void confirmingTheDeleteClearsThePhotosThenTheDraft() throws IOException {
        String script = script("guest-diary-demo.js");

        assertThat(section(script,
                "deletePrompt?.querySelector('[data-guest-action=\"confirm-delete\"]')",
                "deletePrompt?.addEventListener('click'"))
                .contains("discardDraft().then((cleared) => {")
                .contains("render();");

        String discard = section(script, "async function discardDraft()", "function show(element");
        // 지우는 차례. draftId 를 잃기 전에 사진부터 거둔다.
        assertThat(List.of(
                discard.indexOf("global.GuestDiaryPhoto?.releaseAll();"),
                discard.indexOf("await photoStore.deletePhotosForDraft(draft.draftId);"),
                discard.indexOf("if (!store.clear()) {"),
                discard.indexOf("intent?.clear();")))
                .doesNotContain(-1)
                .isSorted();
        // 사진 정리가 실패해도 삭제가 멈추지 않는다.
        assertThat(discard).contains("} catch (ignored) {");
        // 체험용 key 만 지운다. 브라우저 저장소를 통째로 비우지 않는다.
        assertThat(script).doesNotContain("localStorage.clear()");
        assertThat(store()).contains("store.removeItem(STORAGE_KEY)");
    }

    /**
     * 지운 뒤에는 카드가 한 장도 남지 않는다.
     *
     * <p>책장 칸(.diary-shelf)은 display 가 정해진 상자라 hidden 만으로는 사라지지 않는다.
     * 그래서 지운 뒤에도 틀로 남겨 둔 빈 카드가 그대로 보였다. (눌러도 열 것이 없어 되돌아왔다)
     * 데이터가 남은 것이 아니라 감추지 못한 것이었으므로, 접는 규칙을 둔다.
     */
    @Test
    void anEmptyShelfShowsNoCardAtAll() throws IOException {
        String css = Files.readString(
                Path.of("src/main/resources/static/css/diary.css"), StandardCharsets.UTF_8);

        assertThat(rule(css, ".diary-shelf")).contains("display: grid;");
        assertThat(rule(css, ".diary-shelf[hidden]")).contains("display: none;");

        String script = script("guest-diary-demo.js");
        String render = section(script, "function render()", "function wireActions()");
        assertThat(render)
                .contains("show(shelf, draft !== null);")
                .contains("show(empty, draft === null);")
                // 없는 다이어리를 기본 표지/기본 제목으로 지어내지 않는다. 그리기 전에 멈춘다.
                .contains("if (!draft) {")
                .contains("show(incomplete, false);")
                .contains("return;");
        // 표지 그리기는 draft 가 있을 때에만 부른다.
        assertThat(render.indexOf("if (!draft) {"))
                .isLessThan(render.indexOf("preview.render("));
        // 카드 마크업은 서버가 접힌 채로 내려준다. (스크립트가 펴 줄 때만 보인다)
        assertThat(resource("templates/diary/demo.html"))
                .contains("<ul class=\"diary-shelf\" hidden data-guest-shelf-list>");
    }

    /** 5) 이 화면은 서버에 닿지 않는다. 회원 여행일기 삭제 경로를 부르지 않는다. */
    @Test
    void deletingAGuestDiaryNeverTouchesTheServer() throws IOException {
        assertThat(script("guest-diary-demo.js"))
                .doesNotContain("fetch(")
                .doesNotContain("XMLHttpRequest")
                .doesNotContain("/delete")
                .doesNotContain("method: 'POST'");
        // 회원 목록의 삭제는 예전 그대로 서버로 간다.
        assertThat(resource("templates/diary/list.html"))
                .contains("th:action=\"@{/diaries/{id}/delete(id=${diary.id})}\" method=\"post\"");
    }

    /** 6) 기본정보가 없는 체험 여행일기로 편집 화면에 오면 그 사실과 갈 자리를 먼저 알린다. */
    @Test
    void theEditorSaysWhatIsMissingBeforeAnythingElse() throws IOException {
        String template = resource("templates/diary/demo-edit.html");

        assertThat(template)
                .contains("data-guest-basics-missing")
                .contains("여행일기 정보를 먼저 입력해 주세요.")
                .contains("제목과 여행기간을 설정해야 페이지 날짜를 지정할 수 있습니다.")
                .contains("th:href=\"@{/diaries/demo/new(mode=complete,returnTo=edit)}\"");

        String editor = script("guest-diary-editor.js");
        // 판정은 만들기 화면과 같은 규칙을 그대로 쓴다. 여기에서 다시 만들지 않는다.
        assertThat(editor)
                .contains("return store.isComplete(store.getDraft());")
                .contains("show(page.querySelector('[data-guest-basics-missing]'), !ready);")
                // 기간이 없으면 "이 장의 날짜" 안내는 띄우지 않는다. 무엇부터 할지 헷갈린다.
                .contains("show(page.querySelector('[data-guest-page-date-missing]'), ready && undated);");
    }

    /** 7) 기본정보가 없는 동안에는 페이지 날짜를 넣어 우회할 수 없다. 이유와 갈 자리도 함께 보인다. */
    @Test
    void thePageDateCannotBeSetWhileTheBasicsAreMissing() throws IOException {
        String editor = script("guest-diary-editor.js");

        // 날짜 칸을 잠그고 왜 잠겼는지 알린다.
        assertThat(editor)
                .contains("date.disabled = !ready;")
                .contains("show(page.querySelector('[data-guest-page-date-locked]'), !ready);");
        // 저장 요청에도 날짜를 담지 않는다. (배경·종이색은 그대로 고칠 수 있다)
        assertThat(section(editor,
                "action('save-page-settings').addEventListener", "action('login')"))
                .contains("if (basicsReady()) {")
                .contains("patch.pageDate = page.querySelector('[data-guest-page-date]').value || null;");
        // 새 장을 여는 길도 같은 안내로 보낸다. 고르지도 못할 날짜를 묻지 않는다.
        assertThat(section(editor,
                "action('add-page').addEventListener", "action('confirm-add-page')"))
                .contains("if (!basicsReady()) {")
                .contains("[data-guest-basics-missing]");

        assertThat(resource("templates/diary/demo-edit.html"))
                .contains("data-guest-page-date-locked")
                .contains("여행 기간을 먼저 입력해 주세요.");

        // 저장소도 여전히 같은 이유로 거절한다. (화면만 믿지 않는다)
        assertThat(store()).contains("return \"여행 기간을 먼저 입력해 주세요.\";");
    }

    /** 8) 9) 12) 13) 돌아갈 자리는 주소가 아니라 정해진 이름표로만 정해진다. */
    @Test
    void theReturnTargetIsAFixedNameNotAUrl() throws IOException {
        String script = script("guest-diary-new.js");

        assertThat(script)
                .contains("const RETURN_TARGETS = {")
                .contains("edit: 'editUrl',")
                .contains("import: 'importUrl',")
                .contains("shelf: 'shelfUrl'")
                // 모르는 이름표는 책장으로 본다. 받은 주소로 가지 않는다.
                .contains("page.dataset[RETURN_TARGETS[params.get('returnTo')] || 'shelfUrl']")
                .doesNotContain("location.href = params.get");

        // 이름표가 가리키는 자리는 이 화면이 들고 있는 내부 경로뿐이다.
        assertThat(resource("templates/diary/demo-new.html"))
                .contains("th:data-shelf-url=\"@{/diaries/demo}\"")
                .contains("th:data-edit-url=\"@{/diaries/demo/edit}\"")
                .contains("th:data-import-url=\"@{/diaries/import}\"");
        // 가져오기 화면은 예전처럼 자기 자리로 돌아온다.
        assertThat(resource("templates/diary/import.html"))
                .contains("th:href=\"@{/diaries/demo/new(mode=complete,returnTo=import)}\"");
    }

    /** 9) 10) 11) 보완은 기본정보만 고친다. 표지·장·사진은 그대로 남아 이어서 채울 수 있다. */
    @Test
    void completingTheBasicsKeepsEverythingElse() throws IOException {
        String script = script("guest-diary-new.js");

        assertThat(section(script, "function completeDraft(entered)", "function say(message)"))
                .contains("store.updateDraft({")
                .doesNotContain("store.createDraft")
                .doesNotContain("store.clear()")
                .doesNotContain("coverDesign:")
                .doesNotContain("pages:");
        assertThat(section(store(), "function updateDraft(patch)", "function addPage(page)"))
                .contains("pages: draft.pages");
        // 기본정보가 채워진 뒤에는 이미 만들어 둔 "이 장의 날짜" 흐름이 그대로 이어진다.
        assertThat(script("guest-diary-editor.js"))
                .contains("action('set-page-date')")
                .contains("store.pagesMissingDate(store.getDraft())");
    }

    /** 8) 책장의 안내도 그대로 남는다. 삭제가 생겼다고 없어지지 않는다. */
    @Test
    void theShelfStillOffersToCompleteAnIncompleteDraft() throws IOException {
        assertThat(resource("templates/diary/demo.html"))
                .contains("data-guest-incomplete")
                .contains("여행일기 정보가 완성되지 않았습니다.")
                .contains("th:href=\"@{/diaries/demo/new(mode=complete,returnTo=shelf)}\"");
    }

    /**
     * 기본정보를 보완하러 가는 길은 모두 체험 경로다.
     *
     * <p>회원용 {@code /diaries/new} 를 가리키면 로그인 화면으로 끌려간다. 체험 중에는
     * 인증 없이 보완할 수 있어야 하므로, 네 자리 모두 {@code /diaries/demo/new} 로 간다.
     * 돌아갈 자리만 각자의 이름표로 다르다.
     */
    @Test
    void everyCompleteLinkPointsAtTheGuestScreen() throws IOException {
        assertThat(resource("templates/diary/demo.html"))
                .contains("@{/diaries/demo/new(mode=complete,returnTo=shelf)}");
        // 편집 화면은 상단 안내와 잠긴 날짜 칸 두 자리에서 같은 곳으로 보낸다.
        assertThat(resource("templates/diary/demo-edit.html").split(
                "@\\{/diaries/demo/new\\(mode=complete,returnTo=edit\\)}", -1))
                .hasSize(3);
        assertThat(resource("templates/diary/import.html"))
                .contains("@{/diaries/demo/new(mode=complete,returnTo=import)}");

        // 체험 화면 어디에서도 회원용 만들기 주소로 보내지 않는다.
        for (String name : new String[]{
                "templates/diary/demo.html", "templates/diary/demo-edit.html",
                "templates/diary/demo-new.html", "templates/diary/demo-cover.html"}) {
            assertThat(resource(name)).as(name).doesNotContain("@{/diaries/new}");
        }
        // 주소는 서버 템플릿이 내려주고 스크립트는 문자열로 적지 않는다.
        for (String name : new String[]{
                "guest-diary-demo.js", "guest-diary-editor.js",
                "guest-diary-new.js", "guest-diary-import.js"}) {
            assertThat(script(name)).as(name).doesNotContain("'/diaries/");
        }

        /*
          이 규칙을 정하는 matcher 는 경로 뒤 물음표까지 붙인 문자열을 본다.
          쿼리를 허용하지 않으면 ?mode=complete 가 회원 경로로 떨어져 로그인으로 끌려간다.
        */
        assertThat(source("config/SecurityConfig.java"))
                .contains("\"^/diaries/demo(?:/new|/edit|/cover)?(?:\\\\?.*)?$\"");
    }

    /** 14) 15) 서버와 회원 흐름은 이번에도 그대로다. */
    @Test
    void theServerAndTheMemberFlowAreUnchanged() throws IOException {
        assertThat(source("service/diary/GuestDiaryImportService.java"))
                .contains("diary.setTitle(requireTitle(manifest.title()));")
                .contains("prepared.setPageDate(requireDate(page.pageDate(), \"페이지 날짜\"));")
                .doesNotContain("LocalDate.now()");
        // 체험 화면을 내려주는 컨트롤러에는 여전히 저장용 endpoint 가 없다.
        assertThat(source("controller/diary/GuestDiaryDemoController.java"))
                .doesNotContain("@PostMapping")
                .doesNotContain("@DeleteMapping");
        assertThat(source("controller/diary/DiaryController.java"))
                .contains("@PostMapping(\"/{diaryId:\\\\d+}/delete\")");
    }

    private String script(String name) throws IOException {
        return resource("static/js/" + name);
    }

    private String store() throws IOException {
        return script("guest-diary-draft-store.js");
    }

    /** CSS 규칙 한 덩어리. */
    private String rule(String css, String selector) {
        int start = css.indexOf("\n" + selector + " {");
        assertThat(start).as("규칙을 찾지 못했습니다: " + selector).isNotNegative();
        return css.substring(start, css.indexOf('}', start));
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
