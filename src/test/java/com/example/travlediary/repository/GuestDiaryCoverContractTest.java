package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비회원 체험 표지 편집의 계약.
 *
 * <p>빌드에 JS 런타임이 없어 실행 대신 규칙을 문장으로 고정한다.
 * 지키는 것은 세 가지다. 표지 편집이 브라우저 안에서 끝난다는 것,
 * 체험 표지는 언제나 한 장이라는 것, 그리고 회원 표지 보관함이 그대로라는 것.
 */
class GuestDiaryCoverContractTest {

    /** 3) 체험 표지 편집기는 서버 저장 endpoint 를 부르지 않는다. */
    @Test
    void theGuestCoverEditorNeverTalksToTheServer() throws IOException {
        assertThat(script("guest-diary-cover-editor.js"))
                .doesNotContain("fetch(")
                .doesNotContain("XMLHttpRequest")
                .doesNotContain("$.ajax")
                .doesNotContain("FormData")
                .doesNotContain("/uploads")
                // 회원 표지 저장 경로를 들고 있지 않다.
                .doesNotContain("/diaries/cover-designs");

        String template = resource("templates/diary/demo-cover.html");
        assertThat(template)
                .doesNotContain("/diaries/cover-designs")
                .doesNotContain("method=\"post\"")
                .doesNotContain("<form");
        // 저장 주소는 네트워크가 아니라 저장소를 가리키는 이름표다.
        assertThat(template).contains("data-create-url=\"/guest/cover/elements/sticker\"")
                .contains("'/guest/cover/elements/label'");
    }

    /** 4) 표지는 draft 의 coverDesign 한 자리에만 저장된다. */
    @Test
    void theCoverIsStoredInTheSingleDraftCoverDesign() throws IOException {
        String editor = script("guest-diary-cover-editor.js");

        assertThat(editor)
                .contains("store.getCoverDesign()")
                .contains("store.updateCoverDesign(")
                .contains("store.addCoverElement(")
                .contains("store.updateCoverElement(")
                .contains("store.removeCoverElement(")
                // 저장 key 를 따로 만들지 않는다.
                .doesNotContain("localStorage");

        // 체험 표지는 한 장뿐이다. 디자인 목록/이름/삭제 개념이 없다.
        String store = script("guest-diary-draft-store.js");
        assertThat(store)
                .contains("coverDesign: normalizeCoverDesign(raw.coverDesign)")
                .doesNotContain("coverDesigns");
        // 화면에 그려지는 부분에는 디자인 이름/목록/삭제가 없다. (주석은 설명이라 뺀다)
        String coverTemplate = resource("templates/diary/demo-cover.html")
                .replaceAll("(?s)<!--.*?-->", "");
        assertThat(coverTemplate)
                .doesNotContain("디자인 이름")
                .doesNotContain("내 표지 디자인")
                .doesNotContain("디자인 저장")
                .doesNotContain("이 디자인 삭제")
                .doesNotContain("cover-design-name")
                // 완료는 "표지 적용하기" 하나뿐이다.
                .contains("data-guest-cover-done")
                .contains("표지 적용하기");
    }

    /**
     * 5) 6) 7) 새로고침 뒤에도 되살아난다.
     * 저장된 요소가 회원 응답과 같은 이름으로 되돌아가고, 자리/크기/회전/겹침이 보존된다.
     */
    @Test
    void theSavedCoverElementsAreRestoredWithTheirGeometry() throws IOException {
        String editor = script("guest-diary-cover-editor.js");
        String restore = editor.substring(editor.indexOf("function restoredPayload(element)"));

        assertThat(editor).contains("const render = renderers[element.elementType];");
        assertThat(restore)
                .contains("if (element.elementType === 'STICKER')")
                .contains("if (element.elementType === 'TEXT')")
                .contains("positionX: element.positionX")
                .contains("positionY: element.positionY")
                .contains("width: element.width")
                .contains("height: element.height")
                .contains("rotation: element.rotation")
                .contains("zIndex: element.zIndex")
                .contains("urls: elementUrls(element.elementId)")
                // 마스킹테이프인지와 조각 경로까지 되돌려야 같은 모습으로 그려진다.
                // (판정은 저장된 그림 경로 하나로 한다 — 서버가 쓰는 규칙과 같다)
                .contains("maskingTape: tape.maskingTape")
                .contains("payload.repeat = tape.repeat;");

        // store 도 같은 필드 이름으로 표지 요소를 담는다.
        assertThat(script("guest-diary-draft-store.js"))
                .contains("function addCoverElement(element)")
                .contains("newId(\"gc_\")");
    }

    /**
     * 12) 되살리기는 마크업 생성기/조작 엔진이 등록된 뒤에 돈다.
     * (2단계에서 겪은 순서 버그를 표지에서도 막는다)
     */
    @Test
    void theCoverRestoreRunsAfterTheRenderersAreRegistered() throws IOException {
        String editor = script("guest-diary-cover-editor.js");

        // 생성기는 각 모듈의 DOMContentLoaded 안에서 등록된다.
        for (String name : new String[]{"diary-sticker-picker.js", "diary-label-picker.js"}) {
            String picker = script(name);
            assertThat(picker.indexOf("window.diaryElementRenderers")).as(name)
                    .isGreaterThan(picker.indexOf("document.addEventListener('DOMContentLoaded'"));
        }
        assertThat(editor)
                .contains("document.addEventListener('DOMContentLoaded', restoreElements);")
                // 조작 엔진의 첫 훑기는 끝난 뒤라 하나씩 넘겨 줘야 한다.
                .contains("global.diaryCanvas?.register(item);")
                .contains("global.diaryTape?.render(item);");
        // 재질/색 칠하기는 반대로 즉시 돈다.
        assertThat(editor).contains("    renderCoverSettings();\n    wireSettings();");

        // 체험 표지 편집기가 마지막 defer 스크립트라야 그 순서가 성립한다.
        String template = resource("templates/diary/demo-cover.html");
        for (String name : new String[]{
                "diary-canvas-drag.js", "diary-tape-repeat.js",
                "diary-sticker-picker.js", "diary-label-picker.js"}) {
            assertThat(template.indexOf("/js/guest-diary-cover-editor.js")).as(name)
                    .isGreaterThan(template.indexOf("/js/" + name));
        }
    }

    /** 8) static 스티커 경로는 보존되고 data: 는 거부된다. */
    @Test
    void staticStickerPathsSurviveWhileDataUrlsAreRejected() throws IOException {
        assertThat(script("guest-diary-draft-store.js"))
                .contains("imageUrl: safeImageUrl(raw.imageUrl)")
                .contains("url.slice(0, 5).toLowerCase() === \"data:\"");
        // 붙일 때 실제 그림 경로가 목록에서 실려 온다. (서버가 붙여 주지 않으므로)
        assertThat(script("guest-diary-cover-editor.js"))
                .contains("imageUrl: option.dataset.stickerImage");
        assertThat(resource("templates/diary/cover-sticker-picker.html"))
                .contains("th:data-sticker-image=\"${sticker.imageUrl}\"");
    }

    /** 9) 표지 사진은 서버로 올라가지 않는다. 고른 파일은 IndexedDB 로만 간다. */
    @Test
    void theGuestCoverPhotoNeverLeavesTheBrowser() throws IOException {
        String template = resource("templates/diary/demo-cover.html");

        // 파일을 고르는 칸은 있지만 보낼 곳이 없다. (form 도 create-url 도 없다)
        assertThat(template)
                .contains("data-guest-photo-input")
                .doesNotContain("<form")
                .doesNotContain("data-create-url=@{")
                .doesNotContain("elements/photo")
                .doesNotContain("enctype=\"multipart/form-data\"");
        assertThat(script("guest-diary-cover-editor.js"))
                .doesNotContain("readAsDataURL")
                .doesNotContain("FileReader")
                // 사진은 원본을 읽어야 해서 따로 되살린다.
                .contains("function restorePhoto(element)");
    }

    /** 10) 꾸민 표지는 책장 카드에 보이고, 그 수정도 카드에서 연다. */
    @Test
    void theShelfCardShowsTheCustomCoverAndOpensItsEditor() throws IOException {
        // 표지는 다이어리 한 권의 속성이라 책장 카드의 ⋯ 메뉴에서 연다.
        assertThat(resource("templates/diary/demo.html"))
                .contains("class=\"diary-book-menu\"")
                .contains("<a class=\"diary-book-menu-item\" role=\"menuitem\"\n"
                        + "                 th:href=\"@{/diaries/demo/cover}\">표지 수정</a>")
                .contains("data-guest-book-cover");
        assertThat(script("guest-diary-demo.js"))
                .contains("preview.render(book, cover, draft);");

        /*
          페이지 편집 화면에는 표지 진입점이 없다. 본문·꾸미기·페이지 관리에만 쓴다.
          (어디로 옮겼는지 적어 둔 주석은 화면에 그려지지 않으므로 빼고 본다)
        */
        assertThat(resource("templates/diary/demo-edit.html")
                .replaceAll("(?s)<!--.*?-->", ""))
                .doesNotContain("/diaries/demo/cover")
                .doesNotContain("표지 수정")
                .doesNotContain("표지 꾸미기")
                .doesNotContain("data-guest-cover-preview");
        assertThat(script("guest-diary-editor.js"))
                .doesNotContain("GuestDiaryCoverPreview")
                .doesNotContain("renderCoverPreview");
        // 표지 적용하기는 책장으로 보내기만 한다. designId 를 만들지 않는다.
        assertThat(resource("templates/diary/demo-cover.html"))
                .contains("<a class=\"diary-form-submit\" data-guest-cover-done\n"
                        + "               th:href=\"@{/diaries/demo}\">표지 적용하기</a>");
    }

    /** 14) 사용자가 쓴 글은 표지 미리보기에서도 innerHTML 로 넣지 않는다. */
    @Test
    void coverTextIsNeverInjectedAsHtml() throws IOException {
        for (String name : new String[]{"guest-diary-cover-editor.js",
                "guest-diary-editor.js", "guest-diary-cover-preview.js",
                "guest-diary-demo.js", "guest-diary-new.js"}) {
            assertThat(script(name).lines()
                    .filter(line -> line.contains("innerHTML ="))
                    .map(String::strip).toList())
                    .as(name)
                    .allMatch(line -> line.equals("content.innerHTML = current.content || '';"));
        }
        // 표지의 글씨도 글자로만 넣는다.
        assertThat(script("guest-diary-cover-preview.js")).contains("span.textContent = text;");
    }

    /**
     * 11) 13) 회원 표지 보관함과 저장 계약은 그대로다.
     * 표지 편집 모듈에도 회원/비회원 분기가 없다.
     */
    @Test
    void theMemberCoverDesignFlowIsUnchanged() throws IOException {
        String memberEditor = resource("templates/diary/cover-design-edit.html");

        // 회원 화면은 예전 저장 경로를 그대로 쓴다.
        assertThat(memberEditor)
                .contains("@{/diaries/cover-designs/{id}/update(id=${designId})}")
                .doesNotContain("@{/diaries/cover-designs/{id}/delete(id=${designId})}")
                .contains("data-create-url=@{/diaries/cover-designs/{id}/elements/sticker")
                .contains("@{/diaries/cover-designs/{id}/elements/photo")
                .contains("/js/diary-cover-photo.js");

        // 공용 모듈은 어느 쪽인지 모른다. 저장 통로 한 곳에서만 갈린다.
        for (String name : new String[]{
                "diary-canvas-drag.js", "diary-sticker-picker.js", "diary-label-picker.js"}) {
            assertThat(script(name)).as(name)
                    .contains("DiarySaveTransport.post")
                    .doesNotContain("TravelDiaryGuestDraftStore")
                    .doesNotContain("isGuest")
                    .doesNotContain("guestMode");
        }
        // 회원 표지 저장은 여전히 서버 컨트롤러가 맡는다.
        assertThat(source("controller/diary/DiaryCoverDesignController.java"))
                .contains("@PostMapping(\"/{designId:\\\\d+}/update\")")
                .contains("@PostMapping(\"/{designId:\\\\d+}/elements/sticker\")");
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
