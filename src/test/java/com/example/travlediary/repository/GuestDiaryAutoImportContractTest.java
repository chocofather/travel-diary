package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증을 마치고 돌아오면 묻지 않고 바로 저장한다.
 *
 * <p>체험 화면에서 "로그인하고 저장하기" 를 누른 것으로 저장하겠다는 뜻은 이미 밝혀졌다.
 * 그런데 인증을 마치고 온 가져오기 화면이 같은 것을 한 번 더 물어보고 있었다.
 *
 * <p>여기에서 고정하는 것은 세 가지다. 묻지 않고 저장하는 기준이 "체험 여행일기가 남아 있는가"
 * 가 아니라 그때 남긴 쪽지라는 것, 시작하기로 정한 뒤에 그 쪽지를 거둔다는 것(실패해도
 * 새로고침으로 되풀이되지 않는다), 그리고 저장하는 길은 예전과 같은 한 곳뿐이라는 것.
 */
class GuestDiaryAutoImportContractTest {

    /** 1) 체험 화면의 [로그인하고 저장하기] 가 쪽지를 남긴다. 뜻도 함께 적는다. */
    @Test
    void theGuestEditorLeavesAnAutoSaveNote() throws IOException {
        assertThat(script("guest-diary-editor.js"))
                .contains("global.GuestDiaryImportIntent.startLogin(store.getDraft()?.draftId);");

        String intent = script("guest-diary-import-intent.js");
        assertThat(intent)
                .contains("const MODE_AUTO = 'AUTO_AFTER_AUTH';")
                .contains("MODE_AUTO: MODE_AUTO,");
        // 쪽지는 여전히 가벼운 표시 하나다. 남기는 것은 번호·시각·뜻뿐이다.
        assertThat(section(intent, "function remember(draftId)", "function clear()"))
                .contains("draftId: draftId,")
                .contains("requestedAt: new Date().toISOString(),")
                .contains("mode: MODE_AUTO")
                .doesNotContain("title")
                .doesNotContain("photoRef")
                .doesNotContain("pages");
    }

    /** 2) 3) 4) 쪽지가 이 여행일기를 가리킬 때만 묻지 않고 저장한다. */
    @Test
    void onlyAMatchingNoteStartsTheSaveWithoutAsking() throws IOException {
        String auto = section(script("guest-diary-import.js"),
                "function startAutomaticImport()", "function readyToSend()");

        assertThat(auto)
                .contains("const requested = intent?.read();")
                // 쪽지가 없거나, 다른 여행일기를 가리키거나, 뜻이 다르면 시작하지 않는다.
                .contains("!requested || !draft || requested.draftId !== draft.draftId")
                .contains("requested.mode !== intent.MODE_AUTO")
                .contains("global.GuestDiaryImport.start();");
        // 체험 여행일기가 남아 있다는 것만으로는 시작하지 않는다.
        assertThat(auto)
                .doesNotContain("store.hasDraft()")
                .doesNotContain("if (draft)");
    }

    /** 6) 시작하기로 정한 뒤에 쪽지를 거둔다. 체험 내용은 이때 건드리지 않는다. */
    @Test
    void theNoteIsConsumedRightBeforeTheSaveStarts() throws IOException {
        String auto = section(script("guest-diary-import.js"),
                "function startAutomaticImport()", "function readyToSend()");

        // 보낼 수 있는지 먼저 보고 → 쪽지를 거두고 → 그 다음에 저장을 시작한다.
        String deciding = auto.substring(auto.indexOf("if (!readyToSend()) {"));
        assertThat(List.of(
                deciding.indexOf("intent.clear();"),
                deciding.indexOf("showAutomatic(true);"),
                deciding.indexOf("global.GuestDiaryImport.start();")))
                .doesNotContain(-1)
                .isSorted();
        // 7) 서버가 답하기 전에는 체험 내용을 하나도 지우지 않는다.
        assertThat(auto)
                .doesNotContain("store.clear()")
                .doesNotContain("deletePhotosForDraft")
                .doesNotContain("releaseAll");
    }

    /** 12) 15) 그대로 보낼 수 없는 상태면 시작하지 않고, 쪽지도 남겨 둔다. */
    @Test
    void anIncompleteDraftIsNeverPostedAutomatically() throws IOException {
        String script = script("guest-diary-import.js");

        assertThat(section(script, "function readyToSend()", "function showAutomatic(running)"))
                .contains("store.isComplete(draft)")
                .contains("store.pagesMissingDate(draft).length === 0");
        /*
          보낼 수 없으면 그대로 돌아간다. 쪽지를 거두지 않으므로, 화면이 안내하는 자리에서
          마저 채우고 돌아오면 그때 이어서 저장된다.
        */
        String blocked = section(script, "if (!readyToSend()) {", "intent.clear();");
        assertThat(blocked).contains("return;").doesNotContain("clear()");
        // 확인 화면의 보완 안내는 그대로다.
        assertThat(resource("templates/diary/import.html"))
                .contains("data-guest-import-incomplete")
                .contains("data-guest-import-undated");
    }

    /** 5) 자동 저장 중에는 물어볼 것이 없다. 확인 카드와 버튼을 감추고 진행만 보여 준다. */
    @Test
    void theAutomaticScreenShowsProgressInsteadOfButtons() throws IOException {
        assertThat(section(script("guest-diary-import.js"),
                "function showAutomatic(running)", "function render()"))
                .contains("show(autoPanel, running);")
                .contains("show(card, !running);")
                .contains("show(actions, !running);");

        assertThat(resource("templates/diary/import.html"))
                .contains("data-guest-import-auto")
                .contains("class=\"diary-import-spinner\"")
                .contains("여행일기를 저장하고 있어요")
                .contains("체험 여행일기를 내 여행일기로 옮기는 중입니다");
        // 진행 표시는 기존 검색 스피너와 같은 애니메이션을 함께 쓴다.
        String css = read(Path.of("src/main/resources/static/css/diary.css"));
        assertThat(css)
                .contains(".diary-import-progress {")
                .contains("animation: diary-search-spin 700ms linear infinite;");
    }

    /** 9) 10) 11) 실패해도 잃는 것이 없다. 확인 화면으로 돌아가 다시 시도할 수 있다. */
    @Test
    void aFailedAutomaticSaveFallsBackToTheConfirmScreen() throws IOException {
        String script = script("guest-diary-import.js");
        String failure = section(script, "} catch (failure) {", "// 서버가 전부 저장했다.");

        assertThat(failure)
                .contains("say(error, failure.message);")
                .contains("backToConfirm();")
                // 실패 처리에서 지우는 것은 없다.
                .doesNotContain("clear")
                .doesNotContain("delete");

        String back = section(script, "function backToConfirm()", "/* ===== 보내기 ===== */");
        assertThat(back)
                .contains("showAutomatic(false);")
                .contains("[data-guest-import-kept]")
                // 같은 버튼이 [다시 시도] 가 된다. 저장 경로를 새로 만들지 않는다.
                .contains("confirm.dataset.label = confirm.dataset.labelRetry;")
                .doesNotContain("fetch(");

        assertThat(resource("templates/diary/import.html"))
                .contains("data-label-retry=\"다시 시도\"")
                .contains("data-guest-import-kept")
                .contains("작성한 내용은 이 브라우저에 그대로 보관되어 있습니다.");
    }

    /** 13) 17) 저장하는 길은 예전과 같은 한 곳이다. 표도 그대로 쓴다. */
    @Test
    void theAutomaticSaveUsesTheSameOneTimeToken() throws IOException {
        String script = script("guest-diary-import.js");

        // 자동이든 수동이든 같은 start() 를 부른다.
        assertThat(script)
                .contains("confirm?.addEventListener('click', () => global.GuestDiaryImport.start());")
                .contains("global.GuestDiaryImport.start();")
                .contains("if (sending || !draft) return;")
                .contains("form.append('importToken', page.dataset.importToken);");
        // 보내는 자리는 하나뿐이다. (자동용 주소를 새로 만들지 않았다)
        assertThat(script.split("fetch\\(", -1)).hasSize(2);
        // 서버도 같은 표로 두 번 저장하지 않는다.
        assertThat(source("controller/diary/GuestDiaryImportController.java"))
                .contains("if (alreadyImported != null) {")
                .contains("GuestDiaryImportTokens.complete(session, importToken, diaryId);");
        assertThat(source("service/diary/GuestDiaryImportService.java"))
                .contains("public Long importDraft(Long userId,");
    }

    /** 8) 성공 뒤 정리와 이동은 예전 그대로다. */
    @Test
    void aSuccessfulAutomaticSaveKeepsTheExistingCleanup() throws IOException {
        String cleanup = section(script("guest-diary-import.js"),
                "async function discardGuestData()", "function busy(isBusy)");

        assertThat(List.of(
                cleanup.indexOf("releaseAll()"),
                cleanup.indexOf("deletePhotosForDraft(draft.draftId)"),
                cleanup.indexOf("store.clear();"),
                cleanup.indexOf("intent?.clear();")))
                .doesNotContain(-1)
                .isSorted();
        assertThat(script("guest-diary-import.js"))
                .contains("'?imported=' + encodeURIComponent(String(diaryId));");
    }

    /** 5) 14) 쪽지 없이 들어온 경우의 확인 화면은 그대로 남는다. */
    @Test
    void theManualConfirmScreenIsStillThere() throws IOException {
        assertThat(resource("templates/diary/import.html"))
                .contains("작성 중인 여행일기가 있어요")
                .contains("data-guest-import-confirm")
                .contains("data-guest-import-later>나중에 하기</a>");
        // 목록의 [가져오기] 는 쪽지를 만들지 않는 평범한 링크다. (그래서 확인 화면이 뜬다)
        assertThat(resource("templates/diary/list.html"))
                .contains("th:href=\"@{/diaries/import}\">가져오기</a>");
        assertThat(script("guest-diary-import-notice.js"))
                .doesNotContain("remember(")
                .doesNotContain("startLogin");
    }

    /** 15) 인증 성공 뒤의 자리 정하기는 건드리지 않았다. (계정 연결 등 기존 우선순위 유지) */
    @Test
    void theAuthenticationHandoverIsUnchanged() throws IOException {
        assertThat(script("guest-diary-import-intent.js"))
                .contains("const IMPORT_PATH = '/diaries/import';")
                .contains("global.location.href = IMPORT_PATH;")
                // 돌아갈 자리는 고정 내부 경로 하나뿐이다.
                .doesNotContain("document.referrer");
        // 로그인 화면에 붙는 것은 돌아갈 자리를 채우는 일뿐이다.
        assertThat(script("guest-diary-import-login.js"))
                .contains("applyToLoginForm()");
        assertThat(source("config/CustomLoginSuccessHandler.java"))
                .doesNotContain("GuestDiary")
                .doesNotContain("guestImport");
    }

    private String script(String name) throws IOException {
        return resource("static/js/" + name);
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
        return read(Path.of("src/main/resources").resolve(relativePath));
    }

    private String source(String relativePath) throws IOException {
        return read(Path.of("src/main/java/com/example/travlediary").resolve(relativePath));
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
