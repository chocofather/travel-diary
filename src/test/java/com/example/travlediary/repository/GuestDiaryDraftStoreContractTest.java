package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비회원 체험 draft 보관소의 계약.
 *
 * <p>빌드에 JS 런타임이 없어 실행 대신 규칙을 문장으로 고정한다.
 * 여기에서 지키는 건 두 가지다. 체험은 브라우저 안에서만 끝난다는 것과,
 * 한도(다이어리 1개 / 페이지 3개)가 화면이 아니라 저장소 자체에 있다는 것.
 */
class GuestDiaryDraftStoreContractTest {

    /** 3) 체험 다이어리는 한 개뿐이다. localStorage key 하나에 draft 하나만 담는다. */
    @Test
    void onlyOneGuestDraftIsEverStored() throws IOException {
        String store = store();

        assertThat(store)
                .contains("const STORAGE_KEY = \"travelDiary.guestDiaryDraft.v1\";")
                .contains("const SCHEMA_VERSION = 1;");
        // 목록/배열로 여러 draft 를 들고 있지 않다. 새로 만들면 이전 것을 덮어쓴다.
        assertThat(store)
                .doesNotContain("drafts")
                .contains("function createDraft(basics)")
                .contains("draftId: newId(\"gd_\")");
        // 책장도 새로 만들기를 확정한 뒤에야 기존 draft 를 지운다.
        assertThat(demoScript()).contains("store.clear()");
    }

    /** 4) 5) 페이지는 1~3개만. 4번째는 저장소가 직접 거절하고 사유를 돌려준다. */
    @Test
    void theFourthPageIsRefusedByTheStoreItself() throws IOException {
        String store = store();

        assertThat(store)
                .contains("const MAX_PAGES = 3;")
                .contains("if (draft.pages.length >= MAX_PAGES) {")
                .contains("return {ok: false, reason: REASON.PAGE_LIMIT_REACHED, limit: MAX_PAGES};");
        // 읽을 때도 한도를 넘는 값은 잘라낸다. 손으로 고친 localStorage 로 4장이 되지 않는다.
        assertThat(store).contains("pages.slice(0, MAX_PAGES)");
    }

    /** 6) 깨진 JSON 이나 알아볼 수 없는 값이면 화면을 죽이지 않고 비우고 다시 시작한다. */
    @Test
    void aBrokenStoredValueIsClearedInsteadOfThrowing() throws IOException {
        String store = store();

        assertThat(store)
                .contains("JSON.parse(saved)")
                .contains("catch (error) {")
                // 손상된 값은 되살리지 않고 지운다.
                .contains("clear();\n            return null;")
                .contains("function normalizeDraft(raw)");
        // 저장소를 쓸 수 없는 환경(시크릿 모드 등)에서도 예외를 밖으로 던지지 않는다.
        assertThat(store).contains("REASON.STORAGE_UNAVAILABLE");
    }

    /** 7) draft 삭제와 새로 시작이 가능하다. */
    @Test
    void theDraftCanBeClearedAndStartedAgain() throws IOException {
        assertThat(store())
                .contains("function clear()")
                .contains("store.removeItem(STORAGE_KEY)")
                .contains("clear: clear");
        // 책장의 "새로 만들기" 확인을 거친 뒤에만 지운다.
        assertThat(demoScript()).contains("data-guest-action=\"confirm-replace\"");
    }

    /**
     * 8) 체험 코드는 회원 다이어리 저장 endpoint 를 부르지 않는다.
     * 서버로 나가는 요청 자체가 없어야 한다.
     */
    @Test
    void theGuestCodeNeverCallsTheMemberDiaryEndpoints() throws IOException {
        for (String script : new String[]{store(), demoScript()}) {
            assertThat(script)
                    .doesNotContain("fetch(")
                    .doesNotContain("XMLHttpRequest")
                    .doesNotContain("$.ajax")
                    .doesNotContain("$.post")
                    .doesNotContain("/diaries/")
                    .doesNotContain("/uploads");
        }
        // 컨트롤러는 화면과 꾸미기 목록만 내려준다.
        String controller = source("controller/diary/GuestDiaryDemoController.java");
        assertThat(controller)
                .contains("return \"diary/demo\";")
                .contains("return \"diary/demo-edit\";")
                // 저장용 endpoint 를 열지 않는다. GET 화면 두 개뿐이다.
                .doesNotContain("@PostMapping")
                .doesNotContain("@PutMapping")
                .doesNotContain("@DeleteMapping");
        // 주입받는 것은 manifest 를 읽는 카탈로그 셋뿐이다. DB 를 타는 협력자가 없다.
        assertThat(controller.lines().filter(line -> line.contains("private final")).toList())
                .containsExactly(
                        "    private final DiaryStickerCatalog diaryStickerCatalog;",
                        "    private final DiaryNoteCatalog diaryNoteCatalog;",
                        "    private final DiaryLabelFontCatalog diaryLabelFontCatalog;");
    }

    /** 9) 사진 원본(base64)은 localStorage 에 넣지 않는다. */
    @Test
    void noPhotoBinaryIsEverStoredInLocalStorage() throws IOException {
        String store = store();

        // data: URI 가 들어오면 읽는 순간 버린다.
        assertThat(store)
                .contains("function safeImageUrl(value)")
                .contains("url.slice(0, 5).toLowerCase() === \"data:\"")
                .doesNotContain("toDataURL")
                .doesNotContain("FileReader")
                .doesNotContain("readAsDataURL");
        // 사진 바이너리 자리는 다음 단계의 IndexedDB 참조 키만 받는다.
        assertThat(store).contains("photoRef: text(raw.photoRef, null)");
    }

    /** 계정정보는 브라우저에 남기지 않는다. */
    @Test
    void noAccountInformationIsKeptInTheDraft() throws IOException {
        assertThat(store())
                .doesNotContain("password")
                .doesNotContain("userEmail")
                .doesNotContain("token")
                .doesNotContain("userId");
    }

    /**
     * 나중에 DB 로 옮기기 쉬운 모양인지. 실제 컬럼과 같은 이름을 쓰되
     * DB 의 AUTO_INCREMENT id 를 흉내 내지 않는다.
     */
    @Test
    void theDraftShapeMatchesTheDiaryTablesWithoutFakingDatabaseIds() throws IOException {
        String store = store();

        // diaries / diary_pages / diary_elements 컬럼과 같은 이름
        assertThat(store).contains(
                "notebookType", "coverStyle", "startDate", "endDate",
                "pageOrder", "pageDate", "backgroundType", "paperColor",
                "pageHeader", "pageHeaderFont", "pageHeaderBold", "content",
                "elementType", "positionX", "positionY", "width", "height",
                "rotation", "zIndex");
        // diary_cover_designs 로 옮길 자리도 형태만 준비되어 있다.
        assertThat(store)
                .contains("function normalizeCoverDesign(raw)")
                .contains("baseCoverStyle")
                .contains("backgroundColor");
        // 식별자는 브라우저 임시값이다. 숫자 증가 id 를 만들지 않는다.
        assertThat(store)
                .contains("newId(\"gp_\")")
                .contains("newId(\"ge_\")")
                .contains("randomUUID")
                .doesNotContain("nextId")
                .doesNotContain("lastId");
    }

    private String store() throws IOException {
        return resource("static/js/guest-diary-draft-store.js");
    }

    private String demoScript() throws IOException {
        return resource("static/js/guest-diary-demo.js");
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
