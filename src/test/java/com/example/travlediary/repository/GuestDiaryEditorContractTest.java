package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비회원 체험 편집기의 계약.
 *
 * <p>빌드에 JS 런타임이 없어 실행 대신 규칙을 문장으로 고정한다.
 * 지키는 것은 세 가지다. 체험 편집이 브라우저 안에서 끝난다는 것,
 * 회원/비회원 갈림이 저장 통로 한 곳에만 있다는 것,
 * 그리고 회원 편집기가 예전과 같은 서버 저장을 그대로 쓴다는 것.
 */
class GuestDiaryEditorContractTest {

    @Test
    void newPageDecorationsUseA5RelativeHeightsAndOffsets() throws IOException {
        String editor = script("guest-diary-editor.js");

        assertThat(editor)
                .contains("const STICKER_HEIGHT = 0.11757;")
                .contains("const TAPE_HEIGHT = 0.05879;")
                .contains("const LABEL_HEIGHT = 0.05226;")
                .contains("const MEMO_HEIGHT = 0.18289;")
                .contains("const OFFSET_Y_STEP = 0.02613;");
    }

    /**
     * 3) 다이어리는 만들기 화면에서 만들어진다. 첫 장까지 함께 열린다.
     * 페이지 편집기가 몰래 만들지 않는다.
     */
    @Test
    void theDiaryIsCreatedOnTheCreateScreenWithItsFirstPage() throws IOException {
        String create = script("guest-diary-new.js");

        assertThat(create)
                .contains("store.createDraft({")
                // 첫 장의 날짜는 만들기 화면에서 입력받은 여행 시작일이다.
                .contains("store.addPage({pageDate: entered.startDate})")
                // 고른 갈래에 따라 책장으로 가거나 표지 편집기로 이어진다.
                .contains("coverChoice === 'CUSTOM'")
                .contains("page.dataset.coverUrl")
                .contains("page.dataset.shelfUrl");
        assertThat(resource("templates/diary/demo-new.html"))
                .contains("th:data-shelf-url=\"@{/diaries/demo}\"")
                .contains("th:data-cover-url=\"@{/diaries/demo/cover}\"");
    }

    /** 4) 체험 편집기는 1단계 store 를 그대로 쓴다. 저장소를 새로 만들지 않는다. */
    @Test
    void theGuestEditorUsesTheExistingDraftStore() throws IOException {
        String editor = script("guest-diary-editor.js");

        assertThat(editor)
                .contains("global.TravelDiaryGuestDraftStore")
                .contains("store.addPage")
                .contains("store.removePage")
                .contains("store.updatePage")
                .contains("store.addElement")
                .contains("store.updateElement")
                .contains("store.removeElement");
        // 저장 key 를 따로 만들지 않는다. 화면 상태도 draft 안에 둔다.
        assertThat(editor)
                .doesNotContain("localStorage")
                .contains("store.setCurrentPageId");
    }

    /**
     * 5) 11) 체험 편집 중에는 서버로 나가는 저장 요청이 없다.
     * 사진도 파일을 고르는 칸 자체를 두지 않아 업로드 경로가 생기지 않는다.
     */
    @Test
    void theGuestEditorNeverTalksToTheServer() throws IOException {
        for (String name : new String[]{
                "guest-diary-editor.js", "guest-diary-demo.js", "guest-diary-new.js"}) {
            assertThat(script(name)).as(name)
                    .doesNotContain("fetch(")
                    .doesNotContain("XMLHttpRequest")
                    .doesNotContain("$.ajax")
                    .doesNotContain("FormData")
                    .doesNotContain("/uploads")
                    .doesNotContain("readAsDataURL")
                    .doesNotContain("toDataURL");
        }

        String template = resource("templates/diary/demo-edit.html");
        // 회원 편집기의 저장 경로(/diaries/{id}/...)를 화면이 들고 있지 않다.
        // 사진 고르개는 있지만 보낼 곳(form/action)이 없다.
        assertThat(template)
                .doesNotContain("/diaries/{diaryId}")
                .doesNotContain("elements/photo")
                .doesNotContain("enctype=\"multipart/form-data\"");
        // 저장 주소는 네트워크가 아니라 저장소를 가리키는 이름표다.
        assertThat(template).contains("data-create-url=\"/guest/elements/sticker\"");
    }

    /** 6) 7) 4번째 장은 store 가 거절하고, 화면은 조용히 넘어가지 않고 로그인 안내를 연다. */
    @Test
    void theFourthPageOpensTheLoginPromptInsteadOfFailingSilently() throws IOException {
        String editor = script("guest-diary-editor.js");

        assertThat(editor)
                .contains("if (result.reason === store.REASON.PAGE_LIMIT_REACHED) {")
                .contains("openPrompt();");
        String template = resource("templates/diary/demo-edit.html");
        assertThat(template)
                .contains("data-guest-prompt")
                .contains("최대 3페이지까지 작성할 수 있어요")
                .contains("로그인하면 계속해서 여행일기를 만들 수 있습니다")
                // 로그인 / 회원가입 / 닫기 세 갈래를 준다.
                .contains("data-guest-action=\"close-prompt\"")
                .contains("@{/users/register}")
                .contains("@{/login}")
                // 아직 DB 로 옮기지 않는다는 안내도 함께 둔다.
                .contains("이 브라우저에 임시로 보관됩니다");
    }

    /** 8) 장 추가/삭제/전환이 모두 draft 안에서 끝난다. */
    @Test
    void pagesCanBeAddedRemovedAndSwitchedInsideTheDraft() throws IOException {
        String editor = script("guest-diary-editor.js");

        assertThat(editor)
                .contains("function openPage(pageId)")
                .contains("store.setCurrentPageId(pageId);")
                .contains("store.removePage(current.pageId)")
                // 장을 바꿀 때는 화면을 다시 연다. 본문 Quill 을 뜯어 고치지 않는다.
                .contains("global.location.reload();");
        assertThat(resource("templates/diary/demo-edit.html"))
                .contains("data-guest-action=\"add-page\"")
                .contains("data-guest-action=\"delete-page\"")
                .contains("data-guest-action=\"previous-page\"")
                .contains("data-guest-action=\"next-page\"");
    }

    /** 9) 10) 붙인 요소가 자리/크기/회전/겹침까지 draft 에 그대로 남는다. */
    @Test
    void everyElementKeepsItsGeometryInTheDraft() throws IOException {
        String editor = script("guest-diary-editor.js");

        // 붙이기: 스티커 / 라벨·메모지(NOTE) / 라벨기 글씨(TEXT)
        assertThat(editor)
                .contains("elementType: 'STICKER'")
                .contains("elementType: 'NOTE'")
                .contains("elementType: 'TEXT'");
        // 옮기기 / 크기 / 회전 / 겹침 / 글 수정이 모두 저장으로 이어진다.
        assertThat(editor)
                .contains("patch.positionX = Number(fields.positionX);")
                .contains("patch.width = Number(fields.width);")
                .contains("patch.rotation = Number(fields.rotation);")
                .contains("function moveLayer(elementId, direction)")
                .contains("patch.textContent =");
        // store 도 같은 필드 이름으로 담는다.
        assertThat(script("guest-diary-draft-store.js")).contains(
                "positionX", "positionY", "width", "height", "rotation", "zIndex");
    }

    /** 11) 페이지 사진은 서버로 올라가지 않는다. 고른 파일은 IndexedDB 로만 간다. */
    @Test
    void theGuestPagePhotoNeverLeavesTheBrowser() throws IOException {
        // 파일을 고르는 칸은 있지만 보낼 곳이 없다. (회원 화면의 사진 form 이 아니다)
        assertThat(resource("templates/diary/demo-edit.html"))
                .contains("data-guest-photo-input")
                .doesNotContain("class=\"diary-photo-add\"")
                .doesNotContain("enctype=\"multipart/form-data\"");
        assertThat(script("guest-diary-editor.js"))
                .doesNotContain("readAsDataURL")
                .doesNotContain("FileReader")
                // 사진은 원본을 읽어야 해서 따로 되살린다.
                .contains("function restorePhoto(element)")
                // 뗀 사진의 원본도 함께 정리한다.
                .contains("releasePhoto(removedPhotoRef);");
    }

    /**
     * 12) 회원 편집기는 예전과 같은 서버 저장을 그대로 쓴다.
     * 갈림은 저장 통로 한 곳에만 있고, 편집 모듈에는 회원/비회원 분기가 없다.
     */
    @Test
    void theMemberEditorKeepsItsServerSaveAndHasNoGuestBranching() throws IOException {
        String transport = script("diary-save-transport.js");

        // 통로의 기본 구현이 예전 저장 요청 그대로다.
        assertThat(transport)
                .contains("method: 'POST'")
                .contains("credentials: 'same-origin'")
                .contains("[csrfHeader]: csrfToken")
                .contains("/login?redirect=");
        // 끼워 넣지 않으면 언제나 서버로 간다.
        assertThat(transport).contains("return handler === null;");

        // 편집 모듈들은 어느 쪽인지 모른다.
        for (String name : new String[]{
                "diary-editor.js", "diary-canvas-drag.js", "diary-sticker-picker.js",
                "diary-note-picker.js", "diary-label-picker.js", "diary-note-text.js"}) {
            assertThat(script(name)).as(name)
                    .contains("DiarySaveTransport.post")
                    .doesNotContain("fetch(")
                    .doesNotContain("TravelDiaryGuestDraftStore")
                    .doesNotContain("isGuest")
                    .doesNotContain("guestMode");
        }

        // 회원 화면도 같은 통로를 싣는다. 저장 주소는 예전 그대로다.
        String detail = resource("templates/diary/detail.html");
        assertThat(detail)
                .contains("/js/diary-save-transport.js")
                .contains("data-create-url=@{/diaries/{diaryId}/pages/{pageId}/elements/sticker")
                .contains("th:data-position-url=\"@{/diaries/{diaryId}/pages/{pageId}"
                        + "/elements/{elementId}/position");
    }

    /**
     * 저장해 둔 꾸미기 요소 되살리기.
     *
     * <p>버그: 되살리기가 마크업 생성기(diaryElementRenderers)보다 먼저 돌아
     * 저장돼 있던 스티커/라벨/메모지가 전부 조용히 버려졌다. 본문만 살아남아
     * "스티커만 사라진다" 로 보였다. 그 순서를 여기에서 고정한다.
     */
    @Test
    void savedElementsAreRestoredAfterTheRenderersAreRegistered() throws IOException {
        String editor = script("guest-diary-editor.js");

        // 마크업 생성기는 각 모듈의 DOMContentLoaded 안에서 등록된다.
        for (String name : new String[]{
                "diary-sticker-picker.js", "diary-note-picker.js", "diary-label-picker.js"}) {
            String picker = script(name);
            assertThat(picker.indexOf("window.diaryElementRenderers")).as(name)
                    .isGreaterThan(picker.indexOf("document.addEventListener('DOMContentLoaded'"));
        }
        // 그래서 되살리기는 즉시 돌지 않고 그 뒤로 미룬다.
        assertThat(editor)
                .contains("document.addEventListener('DOMContentLoaded', restoreElements);")
                .contains("function restoreElements()");
        // 종이/본문 채우기는 반대로 Quill·조작 엔진보다 먼저라야 해서 그대로 즉시 돈다.
        assertThat(editor).contains("    renderCurrentPage();\n    wireActions();");
        assertThat(editor.substring(editor.indexOf("function renderCurrentPage()")))
                .doesNotContain("restoreElements();");

        // 체험 편집기가 마지막 defer 스크립트라야 그 순서가 성립한다.
        String template = resource("templates/diary/demo-edit.html");
        for (String name : new String[]{
                "diary-canvas-drag.js", "diary-tape-repeat.js", "diary-sticker-picker.js",
                "diary-note-picker.js", "diary-label-picker.js"}) {
            assertThat(template.indexOf("/js/guest-diary-editor.js")).as(name)
                    .isGreaterThan(template.indexOf("/js/" + name));
        }
    }

    /**
     * 되살린 요소도 옮기기/크기/회전이 붙어야 한다.
     * 조작 엔진의 첫 훑기는 캔버스가 비어 있을 때 끝나므로 하나씩 넘겨 줘야 한다.
     */
    @Test
    void restoredElementsAreHandedToTheCanvasEngine() throws IOException {
        assertThat(script("guest-diary-editor.js"))
                .contains("global.diaryCanvas?.register(item);")
                .contains("global.diaryTape?.render(item);");
    }

    /**
     * 스티커/마스킹테이프/라벨·메모지가 모두 같은 되살리기 경로를 탄다.
     * 저장된 값이 붙일 때와 같은 모양(회원 서버 응답과 같은 필드 이름)으로 되돌아간다.
     */
    @Test
    void everyDecorationTypeGoesThroughTheSameRestorePath() throws IOException {
        String editor = script("guest-diary-editor.js");
        String restore = editor.substring(editor.indexOf("function restoredPayload(element)"));

        // 종류를 고르는 곳은 한 군데뿐이다. 요소별 임시 처리를 따로 두지 않는다.
        assertThat(editor).contains("const render = renderers[element.elementType];");
        assertThat(restore)
                .contains("if (element.elementType === 'STICKER')")
                .contains("if (element.elementType === 'NOTE')")
                .contains("if (element.elementType === 'TEXT')");

        // 회원 응답과 같은 이름으로 되돌린다. (renderSticker/renderNote/renderLabel 가 쓰는 이름)
        assertThat(restore)
                .contains("id: element.elementId")
                .contains("positionX: element.positionX")
                .contains("positionY: element.positionY")
                .contains("width: element.width")
                .contains("height: element.height")
                .contains("rotation: element.rotation")
                .contains("zIndex: element.zIndex")
                .contains("urls: elementUrls(element.elementId)")
                .contains("imageUrl: element.imageUrl")
                // 마스킹테이프는 되풀이 조각 경로까지 되돌려야 같은 모습으로 그려진다.
                // (판정은 저장된 그림 경로 하나로 한다 — 서버가 쓰는 규칙과 같다)
                .contains("maskingTape: tape.maskingTape")
                .contains("payload.repeat = tape.repeat;");
    }

    /** 저장된 스티커의 그림 경로가 읽기 과정에서 사라지지 않는다. (data: 만 버린다) */
    @Test
    void theStickerImagePathSurvivesTheRoundTrip() throws IOException {
        String store = script("guest-diary-draft-store.js");

        assertThat(store)
                .contains("imageUrl: safeImageUrl(raw.imageUrl)")
                // 버리는 것은 base64(data:) 하나뿐이다. 보통의 static 경로는 그대로 남는다.
                .contains("url.slice(0, 5).toLowerCase() === \"data:\"");
        // 붙일 때 실제 그림 경로가 목록에서 실려 온다. (서버가 붙여 주지 않으므로)
        assertThat(script("guest-diary-editor.js"))
                .contains("imageUrl: option.dataset.stickerImage");
        assertThat(resource("templates/diary/demo-edit.html"))
                .contains("th:data-sticker-image=\"${sticker.imageUrl}\"");
    }

    /** 체험 편집기는 회원 화면과 같은 마크업 생성기를 다시 쓴다. 별도 렌더러를 만들지 않는다. */
    @Test
    void theGuestEditorReusesTheExistingElementRenderers() throws IOException {
        for (String name : new String[]{
                "diary-sticker-picker.js", "diary-note-picker.js", "diary-label-picker.js"}) {
            assertThat(script(name)).as(name).contains("window.diaryElementRenderers");
        }
        assertThat(script("guest-diary-editor.js"))
                .contains("global.diaryElementRenderers")
                .contains("const item = render(payload);");
    }

    /** 13) 사용자 텍스트는 innerHTML 로 넣지 않는다. */
    @Test
    void userTextIsNeverInjectedAsHtml() throws IOException {
        String editor = script("guest-diary-editor.js");

        assertThat(editor)
                .contains(".textContent = current.pageDate")
                .contains("header.value = current.pageHeader");
        // innerHTML 로 값을 넣는 자리는 Quill 이 붙기 전 본문 한 줄뿐이다.
        assertThat(editor.lines().filter(line -> line.contains("innerHTML ="))
                .map(String::strip).toList())
                .containsExactly("content.innerHTML = current.content || '';");
    }

    private String script(String name) throws IOException {
        return resource("static/js/" + name);
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
