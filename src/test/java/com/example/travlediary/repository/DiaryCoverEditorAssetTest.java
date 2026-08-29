package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 표지 꾸미기는 페이지 다꾸의 엔진을 그대로 쓴다.
 *
 * <p>드래그/크기/회전/겹침/떼기를 표지용으로 다시 만들지 않는다. 엔진은 저장 주소를
 * 요소의 data-* 에서 읽으므로 어느 화면인지 몰라도 되고, 열어 줄 자리만 한 곳 늘었다.
 */
class DiaryCoverEditorAssetTest {

    private static final Path DRAG_JS =
            Path.of("src/main/resources/static/js/diary-canvas-drag.js");
    private static final Path PICKER_JS =
            Path.of("src/main/resources/static/js/diary-sticker-picker.js");
    private static final Path COVER_EDIT =
            Path.of("src/main/resources/templates/diary/cover-design-edit.html");
    private static final Path PAGE_DETAIL =
            Path.of("src/main/resources/templates/diary/detail.html");

    /** 엔진은 한 벌이다. 표지용 사본을 만들지 않는다. */
    @Test
    void theDragEngineIsSharedNotCopied() throws IOException {
        try (var files = Files.list(Path.of("src/main/resources/static/js"))) {
            assertThat(files.map(path -> path.getFileName().toString())
                    .filter(name -> name.contains("canvas-drag")).toList())
                    .as("드래그 엔진 파일").hasSize(1);
        }
        String drag = read(DRAG_JS);
        // 꾸밀 수 있는 자리 두 곳 중 하나라도 열려 있으면 붙는다
        assertThat(drag).contains(".diary-detail-page.is-edit-mode, .diary-cover-canvas.is-editable");
        // 저장 주소는 요소가 들고 온다. 엔진 안에 주소를 적지 않는다
        assertThat(drag).contains("item.dataset.positionUrl").contains("item.dataset.layerUrl");
        assertThat(drag).doesNotContain("/diaries/cover-designs/");
        assertThat(drag).doesNotContain("/pages/");
    }

    /** 스티커 붙이기도 같은 스크립트다. 붙일 자리만 한 곳 늘었다. */
    @Test
    void theStickerPickerKnowsBothPlacesToAttach() throws IOException {
        String picker = read(PICKER_JS);

        assertThat(picker).contains(".diary-book-single .diary-canvas");
        assertThat(picker).contains(".diary-cover-canvas.is-editable .diary-cover-surface");
        // 보낼 주소는 버튼이 알려 준다 (페이지든 표지든 같은 절차)
        assertThat(picker).contains("button.dataset.createUrl");
        assertThat(picker).doesNotContain("/diaries/cover-designs/");
    }

    /** 표지 요소는 엔진이 실제로 읽는 이름 그대로 값을 달고 나온다. */
    @Test
    void coverElementsCarryEveryAttributeTheEngineReads() throws IOException {
        String template = read(COVER_EDIT);

        for (String attribute : new String[]{
                "data-element-id", "data-element-type", "data-sticker-kind",
                "data-position-x", "data-position-y", "data-width", "data-height",
                "data-rotation", "data-z-index",
                "data-position-url", "data-size-url", "data-rotation-url", "data-layer-url"}) {
            assertThat(template).as("%s", attribute).contains("th:" + attribute + "=");
        }
        // 되풀이해서 그리는 마스킹테이프 조각도 페이지 쪽과 같은 이름으로 싣는다
        assertThat(template).contains("th:data-tape-left").contains("th:data-tape-center")
                .contains("th:data-tape-right");
        // 떼기는 요소 행만 지운다 (공용 asset 이라 그림 파일은 그대로 둔다)
        assertThat(template).contains("/elements/{elementId}/sticker/delete");
        // 손잡이/액션 줄도 페이지 다꾸와 같은 클래스를 쓴다
        assertThat(template).contains("diary-rotate-handle").contains("diary-resize-handle")
                .contains("diary-layer-action");
    }

    /**
     * 조작 엔진이 기준 상자를 찾을 수 있어야 한다.
     *
     * <p>엔진은 요소의 기준 상자를 item.closest('.diary-canvas') 로 찾고, 못 찾으면
     * 그 요소에는 조작을 아예 붙이지 않는다. (서버가 그린 것도, 나중에 붙인 것도 마찬가지)
     * 그래서 표지의 자유배치 층도 같은 이름을 함께 달아 둔다.
     */
    @Test
    void theCoverLayerCarriesTheNameTheEngineLooksFor() throws IOException {
        String drag = read(DRAG_JS);
        String template = read(COVER_EDIT);

        assertThat(drag).contains("item.closest('.diary-canvas')");
        assertThat(template).contains("class=\"diary-canvas diary-cover-surface\"");
        // 나중에 붙인 요소도 같은 상자 안에 들어가야 조작이 붙는다
        assertThat(read(PICKER_JS))
                .contains(".diary-cover-canvas.is-editable .diary-cover-surface")
                .contains("window.diaryCanvas?.register(item)");
    }

    /** 좁은 화면에서는 판이 화면 밖으로 나가지 않게 가운데를 기준으로 편다. */
    @Test
    void theStickerPickerStaysOnScreenOnNarrowWidths() throws IOException {
        String css = read(Path.of("src/main/resources/static/css/diary.css"));

        String narrow = css.substring(css.indexOf("@media (max-width: 780px) {"));
        narrow = narrow.substring(0, narrow.indexOf("\n}\n\n@media"));
        assertThat(narrow).contains(".diary-cover-tool-row .diary-sticker-popover")
                .contains("transform: translateX(-50%);");
    }

    /** 사진도 스티커와 같은 엔진·같은 이름으로 다뤄진다. (크기 조절 때 비율을 지키는 근거) */
    @Test
    void coverPhotosUseTheSameEngineAndKeepTheirRatio() throws IOException {
        String drag = read(DRAG_JS);
        String template = read(COVER_EDIT);
        String photoJs = read(Path.of("src/main/resources/static/js/diary-cover-photo.js"));

        // 엔진은 이 값을 보고 원본 비율을 지킬지 정한다
        assertThat(drag).contains("item.dataset.elementType === 'PHOTO'");
        assertThat(template).contains("diary-canvas-photo diary-photo");
        assertThat(photoJs).contains("item.dataset.elementType = 'PHOTO'");
        // 나중에 붙인 사진도 같은 엔진에 넘긴다
        assertThat(photoJs).contains("window.diaryCanvas?.register(item)");
        // 사진은 올린 파일이라 지울 때 파일까지 정리하는 주소를 쓴다
        assertThat(template).contains("/elements/{elementId}/photo/delete");
        assertThat(photoJs).contains("photo.urls.delete");
    }

    /**
     * 표지는 스크롤을 따라다니지 않는다.
     *
     * <p>왼쪽 표지와 오른쪽 설정이 같은 문서 흐름에 있어, 화면을 내리면 둘이 함께 올라간다.
     * (따라다니게 하려고 두었던 자리 고정과 그 보정값은 모두 걷어냈다)
     */
    @Test
    void theCoverScrollsAwayWithTheRestOfThePage() throws IOException {
        String css = read(Path.of("src/main/resources/static/css/diary.css"));
        String template = read(COVER_EDIT);

        // 표지 편집 화면 어디에도 자리 고정이 남아 있지 않다
        for (String selector : new String[]{
                ".diary-cover-preview", ".diary-cover-design-editor", ".diary-cover-canvas"}) {
            assertThat(rule(css, selector)).as("%s", selector)
                    .doesNotContain("position: sticky")
                    .doesNotContain("position: fixed");
        }
        assertThat(css).doesNotContain(".diary-cover-sticky");
        assertThat(template).doesNotContain("cover-sticky");
        // 스크롤을 보고 자리를 고치는 스크립트도 없다
        assertThat(read(Path.of("src/main/resources/static/js/diary-cover-design.js")))
                .doesNotContain("scroll");
    }

    /** 고르는 판은 도구 줄 위로 열린다. (도구가 화면 아래쪽에 있기 때문) */
    @Test
    void theStickerPickerOpensUpwardInTheCoverEditor() throws IOException {
        String css = read(Path.of("src/main/resources/static/css/diary.css"));
        String scoped = rule(css, ".diary-cover-tool-row .diary-sticker-popover");

        assertThat(scoped).contains("bottom: calc(100% + 8px);").contains("top: auto;");
        // 오른쪽 끝을 도구 줄에 맞춘다 (설정 카드 쪽으로 펴지 않는다)
        assertThat(scoped).contains("right: 0;").contains("left: auto;");
        // 화면이 낮아도 판이 위로 넘치지 않게 높이만 접는다
        assertThat(scoped).contains("max-height: calc(100vh - 180px);");
        // 판 크기와 미리보기 값은 페이지 다꾸와 같다 (아래로 여는 규칙도 그대로다)
        assertThat(rule(css, ".diary-sticker-popover"))
                .contains("top: calc(100% + 6px);").contains("left: 0;");
        assertThat(scoped).doesNotContain("width").doesNotContain("z-index");
    }

    /** 꾸미기 도구는 표지 위가 아니라 오른쪽 설정과 같은 자리에 있다. */
    @Test
    void theDecoratingToolsSitWithTheSettingsNotOverTheCover() throws IOException {
        String template = read(COVER_EDIT);
        String css = read(Path.of("src/main/resources/static/css/diary.css"));

        // 도구 세 칸(스티커 / 사진 두 갈래)이 한 줄에 같은 모양으로 놓인다
        assertThat(template).contains("diary-cover-tool-row");
        assertThat(template.split("class=\"diary-cover-tool\"", -1)).hasSize(3);
        // 도구 묶음이 설정 카드 안에 있다 (표지 위에 떠 있지 않다)
        assertThat(template.indexOf("diary-cover-design-panel"))
                .isLessThan(template.indexOf("diary-cover-tools"));
        // 세 칸의 크기와 글자를 한 규칙에서 정한다
        assertThat(rule(css, ".diary-cover-tool")).contains("height: 60px;");
    }

    /** 표지 꾸미는 자리를 실제로 쓸 만한 크기로 둔다. */
    @Test
    void theCoverGetsMoreRoomThanTheSettings() throws IOException {
        String css = read(Path.of("src/main/resources/static/css/diary.css"));

        // 왼쪽(표지)이 남는 폭을 갖고 오른쪽(설정)은 좁게 고정한다
        assertThat(rule(css, ".diary-cover-design-editor"))
                .contains("grid-template-columns: minmax(0, 1fr) minmax(0, 300px);");
        assertThat(rule(css, ".diary-cover-preview")).contains("width: min(420px, 100%);");
    }

    /** 표지의 폴라로이드는 페이지 다꾸 사진의 값을 건드리지 않고 안쪽에서만 얇게 둔다. */
    @Test
    void theCoverPolaroidHasAThinnerFrameThanThePageOne() throws IOException {
        String css = read(Path.of("src/main/resources/static/css/diary.css"));

        assertThat(rule(css, ".diary-cover-surface .diary-photo.is-photo-polaroid"))
                .contains("padding: 3px 3px 7px;");
        // 페이지 다꾸 사진의 값은 그대로다
        assertThat(rule(css, ".diary-photo")).contains("padding: 6px 6px 8px;");
    }

    /** 표지는 속지의 계산을 물려받지 않는다. */
    @Test
    void theCoverDoesNotBorrowThePaperMetrics() throws IOException {
        String template = read(COVER_EDIT);
        String css = read(Path.of("src/main/resources/static/css/diary.css"));

        assertThat(template).doesNotContain("--diary-page-unit").doesNotContain("--diary-line");
        // 재질 변수를 나눠 쓰는 규칙이 앞에 한 번 더 나오므로, 표지 상자를 정하는 규칙에서 찾는다
        String canvas = rule(css.substring(css.indexOf("표지 한 장. 여기가 곧 표지의 좌표계다")),
                ".diary-cover-canvas");
        assertThat(canvas).contains("aspect-ratio: 3 / 4;")
                .contains("container-type: inline-size;")
                .doesNotContain("41 / 38")
                .doesNotContain("--diary-page-unit");
    }

    /** 페이지 다꾸는 예전 그대로다. (이번 기능 때문에 바뀐 곳이 없다) */
    @Test
    void thePageEditorIsUntouched() throws IOException {
        String template = read(PAGE_DETAIL);

        // 페이지 요소의 저장 주소와 캔버스 구조는 그대로다
        assertThat(template).contains("/diaries/{diaryId}/pages/{pageId}/elements/{elementId}/position");
        assertThat(template).contains("class=\"diary-canvas\"");
        assertThat(template).contains("diary/detail :: sheetCanvas");
        // 표지 전용 표시가 페이지 쪽으로 새어 들어가지 않았다
        assertThat(template).doesNotContain("diary-cover-canvas")
                .doesNotContain("diary-cover-surface");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String rule(String css, String selector) {
        int start = css.indexOf("\n" + selector + " {");
        assertThat(start).as("규칙을 찾지 못했습니다: " + selector).isNotNegative();
        return css.substring(start, css.indexOf('}', start));
    }
}
