package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 체험 여행일기를 실제로 옮겨 담을 때 브라우저 쪽이 지켜야 하는 순서.
 *
 * <p>서버 규칙은 단위 검사로 확인한다. 여기에서 붙잡아 두는 것은 자동 검사로 재현하기 어려운
 * "언제 지우는가" 다. 체험 내용은 서버가 전부 저장했다고 답한 뒤에만 지워야 하고,
 * 실패하면 하나도 지우지 않아야 한다. 순서가 뒤집히면 사용자가 쓴 것이 사라진다.
 */
class GuestDiaryImportContractTest {

    /** 35) 보내기 과정에는 지우는 코드가 없다. 읽기만 한다. */
    @Test
    void nothingIsErasedWhileTheRequestIsStillInFlight() throws IOException {
        String sending = scriptSection("guest-diary-import.js",
                "async function send()", "async function discardGuestData()");

        assertThat(sending)
                .contains("photoStore.getPhoto(refs[index])")
                .doesNotContain("store.clear()")
                .doesNotContain("deletePhoto")
                .doesNotContain("removeElement")
                .doesNotContain("updateDraft");
    }

    /**
     * 36) 실패하면 체험 내용이 그대로 남는다.
     *
     * <p>실패 처리에서 하는 일은 세 가지뿐이다 — 버튼을 되돌리고, 진행 안내를 지우고,
     * 이유를 보여 준다. 다시 눌러 볼 수 있어야 하기 때문이다.
     */
    @Test
    void aFailedImportLeavesEverythingWhereItWas() throws IOException {
        String script = script("guest-diary-import.js");
        String failure = section(script,
                "} catch (failure) {", "// 서버가 전부 저장했다.");

        assertThat(failure)
                .contains("busy(false);")
                .contains("say(error, failure.message);")
                .contains("return;")
                .doesNotContain("clear")
                .doesNotContain("delete");
        // 어디에서도 브라우저 저장소를 통째로 비우지 않는다.
        assertThat(script).doesNotContain("localStorage.clear()");
    }

    /** 37) 두 번 눌러도 요청은 한 번이다. 버튼 잠금에만 기대지 않는다. */
    @Test
    void aSecondClickNeverSendsASecondRequest() throws IOException {
        String script = script("guest-diary-import.js");

        // 브라우저 쪽: 보내는 중이면 시작하지 않는다.
        assertThat(script)
                .contains("let sending = false;")
                .contains("if (sending || !draft) return;");
        // 서버 쪽: 같은 표로는 두 번 저장되지 않는다. 이쪽이 진짜 방어선이다.
        assertThat(script).contains("form.append('importToken', page.dataset.importToken);");
        assertThat(source("service/diary/GuestDiaryImportTokens.java"))
                .contains("public static boolean isIssued(HttpSession session, String token)")
                .contains("public static Long completedDiaryId(HttpSession session, String token)");
        assertThat(source("controller/diary/GuestDiaryImportController.java"))
                .contains("if (alreadyImported != null) {")
                .contains("GuestDiaryImportTokens.complete(session, importToken, diaryId);");
    }

    /**
     * 38) 지우는 차례.
     *
     * <p>사진을 먼저 지우고 draft 를 비운다. 순서가 반대면 draftId 를 잃어
     * 어떤 사진을 지워야 하는지 알 수 없게 된다.
     */
    @Test
    void theCleanupHappensOnlyAfterSuccessAndInThisOrder() throws IOException {
        String script = script("guest-diary-import.js");

        // 성공한 뒤에 부른다.
        assertThat(script.indexOf("diaryId = await send();"))
                .isLessThan(script.indexOf("await discardGuestData();"));

        String cleanup = section(script, "async function discardGuestData()", "function busy(");
        assertThat(List.of(
                cleanup.indexOf("releaseAll()"),
                cleanup.indexOf("deletePhotosForDraft(draft.draftId)"),
                cleanup.indexOf("store.clear();"),
                cleanup.indexOf("intent?.clear();")))
                .doesNotContain(-1)
                .isSorted();
        // 정리하다 실패해도 저장 성공을 덮지 않는다.
        assertThat(cleanup).contains("try {").contains("} catch (ignored) {");
    }

    /** 39) 남은 체험 여행일기가 있을 때만 목록 위에 가져오기 안내가 보인다. */
    @Test
    void theShelfOffersTheImportOnlyWhenSomethingIsStillWaiting() throws IOException {
        // 서버는 무엇이 남아 있는지 모른다. 화면은 언제나 접힌 채로 내려간다.
        assertThat(resource("templates/diary/list.html"))
                .contains("hidden data-guest-import-notice");
        assertThat(script("guest-diary-import-notice.js"))
                .contains("notice.hidden = !store.hasDraft();");
        // 서버가 이 판단에 끼어들지 않는다.
        assertThat(source("controller/diary/DiaryController.java"))
                .doesNotContain("guestImport")
                .doesNotContain("hasGuestDraft");
    }

    /* ===== 기존 기능 ===== */

    /**
     * 32) 회원이 평소 쓰던 저장 경로는 그대로다.
     *
     * <p>가져오기는 새 입구를 하나 더한 것이지, 기존 경로를 갈아탄 것이 아니다.
     */
    @Test
    void theMemberSaveRoutesAreUntouched() throws IOException {
        String security = source("config/SecurityConfig.java");

        assertThat(security)
                .contains("\"^/diaries$\", HttpMethod.POST.name()")
                .contains("\"^/diaries/[0-9]+/pages$\", HttpMethod.POST.name()")
                .contains("\"^/diaries/cover-designs$\", HttpMethod.POST.name()");

        // 가져오기 서비스는 가져오기 입구에서만 쓰인다.
        assertThat(referencesTo("GuestDiaryImportService"))
                .containsExactlyInAnyOrder(
                        "service/diary/GuestDiaryImportService.java",
                        "controller/diary/GuestDiaryImportController.java");
    }

    /**
     * 22) 체험 표지는 "내 디자인 보관함" 에 남지 않는다.
     *
     * <p>보관함(diary_cover_designs)은 회원이 직접 만든 표지 원본을 모아 두는 곳이다.
     * 체험 표지는 그 원본이 아니라 이 여행일기에 입힌 표지 자체다.
     */
    @Test
    void theImportNeverWritesToTheCoverDesignLibrary() throws IOException {
        String service = source("service/diary/GuestDiaryImportService.java")
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");

        assertThat(service)
                .contains("diaryCoverMapper.insert(prepared)")
                .contains("diaryCoverElementMapper.insert(prepared)")
                .doesNotContain("CoverDesign");
    }

    /** 23) 브라우저의 임시 사진 참조는 DB 근처에도 가지 않는다. */
    @Test
    void thePhotoReferenceNeverBecomesStoredData() throws IOException {
        // 저장 모델에 그런 칸이 없다.
        for (String model : List.of("model/DiaryElement.java", "model/DiaryCoverElement.java")) {
            assertThat(source(model)).doesNotContain("photoRef");
        }
        // 매퍼에도 없다.
        for (String mapper : List.of("mapper/DiaryElementMapper.xml",
                "mapper/DiaryCoverElementMapper.xml")) {
            assertThat(resource(mapper)).doesNotContain("photoRef").doesNotContain("photo_ref");
        }
        // 서비스는 참조로 올라온 파일을 찾는 데에만 쓰고, 저장하는 것은 새 주소다.
        assertThat(source("service/diary/GuestDiaryImportService.java"))
                .contains("String saved = fileUploadService.saveImportedDiaryPhoto(file, directory);")
                .contains("return saved;");
    }

    /* ===== 도우미 ===== */

    /** 이 파일에서 {@code from} 부터 {@code to} 직전까지의 토막. */
    private String section(String source, String from, String to) {
        int start = source.indexOf(from);
        int end = source.indexOf(to, start + 1);
        assertThat(start).as("'" + from + "' 를 찾지 못했다").isNotNegative();
        assertThat(end).as("'" + to + "' 를 찾지 못했다").isGreaterThan(start);
        return source.substring(start, end);
    }

    private String scriptSection(String scriptName, String from, String to) throws IOException {
        return section(script(scriptName), from, to);
    }

    /** 이 이름을 실제로 쓰는 java 파일. (경로는 com/example/travlediary 아래 기준) */
    private List<String> referencesTo(String typeName) throws IOException {
        Path root = Path.of("src/main/java/com/example/travlediary");
        try (var files = Files.walk(root)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains(typeName))
                    .map(path -> root.relativize(path).toString())
                    .sorted()
                    .toList();
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(path.toString(), exception);
        }
    }

    private String script(String name) throws IOException {
        return resource("static/js/" + name);
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
