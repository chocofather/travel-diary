package com.example.travlediary.controller.diary;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 라벨 / 떡메모지(NOTE)를 종이 위에 그리는 자리.
 *
 * <p>배경과 글자가 한 요소로 함께 움직이고, 자리·크기·회전·겹침 순서는
 * 사진·스티커와 똑같은 상대좌표를 쓴다. 모양만 style_type 으로 갈린다.
 *
 * <p>이번 단계는 그리기까지다. 만들기·지우기·글 고치기는 아직 없다.
 */
class DiaryNoteRenderingAssetTest {

    private static final Path DIARY_CSS = Path.of("src/main/resources/static/css/diary.css");
    private static final Path DETAIL_HTML =
            Path.of("src/main/resources/templates/diary/detail.html");

    @Test
    void aNoteIsDrawnOnTheSameCanvasAsPhotosAndStickers() throws IOException {
        String note = noteFigure();

        assertThat(note)
                .contains("element.elementType == 'NOTE'")
                .contains("class=\"diary-canvas-item diary-note\"")
                .contains("th:data-element-id=\"${element.id}\"")
                .contains("th:data-element-type=\"${element.elementType}\"");
    }

    @Test
    void aNoteUsesTheSameRelativeCoordinatesAsEverythingElse() throws IOException {
        String note = noteFigure();

        /*
          새 좌표 계산을 만들지 않는다.
          저장된 0~1 상대값을 % 로 그대로 쓰므로 읽기(2면)와 편집(1면)이 같은 자리를 가리킨다.
        */
        assertThat(note)
                .contains("left:${element.positionX * 100}%")
                .contains("top:${element.positionY * 100}%")
                .contains("width:${element.width * 100}%")
                .contains("height:${element.height * 100}%")
                .contains("transform:rotate(${element.rotation}deg)")
                .contains("z-index:${element.zIndex}");
        // 자리·크기·회전·겹침을 옮기는 주소도 기존 공통 경로 그대로다
        for (String url : new String[]{
                "position-url", "size-url", "rotation-url", "layer-url"}) {
            assertThat(note).as("%s", url).contains("th:data-" + url);
        }
    }

    @Test
    void theBackgroundAndTheWordsAreOnePieceNotTwoStackedElements() throws IOException {
        String note = noteFigure();

        // 배경 스티커 하나 + 글 요소 하나를 겹치는 방식이 아니다
        assertThat(note)
                .contains("class=\"diary-note-surface\"")
                .contains("class=\"diary-note-text\"")
                .contains("th:text=\"${element.textContent}\"");
        // 사용자가 쓴 값이라 글자로만 넣는다
        assertThat(note).doesNotContain("th:utext");
    }

    @Test
    void theShapeAndColourBothComeFromTheSavedValues() throws IOException {
        String note = noteFigure();

        // 템플릿에 모양·색별 조건문을 늘어놓지 않는다. 모델이 만든 이름을 붙일 뿐이다
        assertThat(note)
                .contains("${element.noteStyleClass}")
                .contains("${element.noteColorClass}");
        assertThat(note)
                .doesNotContain("MEMO_SQUARE").doesNotContain("DATE_LABEL")
                .doesNotContain("IVORY").doesNotContain("SAGE");
    }

    @Test
    void theColourRulesOnlyMoveTheTokensTheShapeRulesRead() throws IOException {
        String css = Files.readString(DIARY_CSS);

        /*
          모양은 색 값을 직접 쓰지 않고 세 칸만 읽는다.
          그래서 모양이 늘어도 색이 늘어도 규칙은 더하기지 곱하기가 아니다.
        */
        assertThat(rule(css, ".diary-note-memo-square .diary-note-surface"))
                .contains("background: var(--diary-note-paper)")
                .contains("border: 1px solid var(--diary-note-line)");
        // 떡메지들이 나눠 쓰는 글자 규칙. 묶음의 마지막 줄로 찾는다
        assertThat(rule(css, ".diary-note-memo-todo .diary-note-text"))
                .contains("color: var(--diary-note-ink)");

        for (String color : new String[]{"ivory", "pink", "sage", "sky"}) {
            String colorRule = rule(css, ".diary-note-color-" + color);
            assertThat(colorRule).as("%s", color)
                    .contains("--diary-note-paper:")
                    .contains("--diary-note-line:")
                    .contains("--diary-note-ink:");
        }

        /*
          색 규칙은 모양 규칙과 우선순위가 같다. 뒤에 와야 색이 이긴다.
          앞에 두면 고른 색이 그 모양의 기본색에 그대로 덮인다.
        */
        assertThat(css.indexOf(".diary-note-color-ivory"))
                .isGreaterThan(css.indexOf(".diary-note-memo-round {"));
    }

    @Test
    void everyNewDesignWorksWithEveryColour() throws IOException {
        String notes = noteStyles(Files.readString(DIARY_CSS));

        for (String shape : new String[]{
                "ticket-label", "border-label", "dashed-label", "tag-label", "check-label",
                "memo-lined", "memo-grid", "memo-dot", "memo-checklist", "memo-todo"}) {
            String block = notes.substring(notes.indexOf(".diary-note-" + shape + " .diary-note-surface"));
            block = block.substring(0, block.indexOf('}'));
            /*
              모양은 색 값을 직접 쓰지 않는다. 세 칸만 읽어야 색 4종과 그대로 조합된다.
              여기에 hex 를 한 번 박으면 그 모양만 색 축에서 떨어져 나간다.
            */
            assertThat(block).as("%s", shape).doesNotContain("#");
        }
    }

    @Test
    void thePatternsAreMeasuredByThePaperNotByTheElement() throws IOException {
        String notes = noteStyles(Files.readString(DIARY_CSS));

        /*
          줄·모눈·도트·체크 칸의 간격은 종이 단위로 잡는다.
          요소 크기(%)로 잡으면 라벨을 늘릴 때 무늬가 함께 늘어나 찌그러진다.
        */
        for (String shape : new String[]{
                "memo-lined", "memo-grid", "memo-dot", "memo-checklist", "check-label"}) {
            String block = notes.substring(notes.indexOf(".diary-note-" + shape + " .diary-note-surface"));
            block = block.substring(0, block.indexOf('}'));
            assertThat(block).as("%s", shape).contains("var(--diary-page-unit)");
        }
        // 줄 메모의 글줄과 줄무늬는 같은 자를 쓴다
        assertThat(notes).contains("--diary-note-rule: calc(20 * var(--diary-page-unit))");
        assertThat(rule(Files.readString(DIARY_CSS),
                ".diary-note-memo-lined .diary-note-text,\n"
                        + ".diary-note-memo-checklist .diary-note-text"))
                .contains("line-height: var(--diary-note-rule)");
    }

    @Test
    void noNewDesignNeedsAPictureFile() throws IOException {
        String notes = noteStyles(Files.readString(DIARY_CSS));

        // 이번에 더한 것은 모두 CSS 로만 그린다. 그림 파일을 들이지 않았다
        assertThat(notes).doesNotContain("url(");
    }

    @Test
    void aNoteWithNoColourKeepsTheLookItAlwaysHad() throws IOException {
        String css = Files.readString(DIARY_CSS);

        /*
          예전에 만든 요소는 색이 비어 있다. 색 class 가 붙지 않으므로
          모양이 스스로 들고 있는 기본색으로 그려진다. (보이던 대로 남는다)
        */
        assertThat(design(css, "/* 사각 떡메:")).contains("--diary-note-paper: #f8f4ea;");
        assertThat(design(css, "/* 둥근 떡메:")).contains("--diary-note-paper: #f8f2ee;");
    }

    @Test
    void allFourDesignsHaveTheirOwnLook() throws IOException {
        String css = Files.readString(DIARY_CSS);

        for (String styleClass : new String[]{
                ".diary-note-date-label", ".diary-note-title-label",
                ".diary-note-memo-square", ".diary-note-memo-round"}) {
            assertThat(css).as("%s", styleClass).contains(styleClass + " .diary-note-surface {");
        }
        // 사각과 둥근 떡메는 모서리로 갈린다
        assertThat(design(css, "/* 사각 떡메:"))
                .contains("border-radius: calc(3 * var(--diary-page-unit));");
        assertThat(design(css, "/* 둥근 떡메:"))
                .contains("border-radius: calc(10 * var(--diary-page-unit));");
        // 날짜와 제목 라벨은 기본 종이색으로 갈린다
        assertThat(design(css, "/* 날짜 라벨:")).contains("--diary-note-paper: #fdfaf2;");
        assertThat(design(css, "/* 제목 라벨:")).contains("--diary-note-paper: #fbf7ef;");
    }

    @Test
    void theWordsAreSizedByThePaperNotByTheScreen() throws IOException {
        String css = Files.readString(DIARY_CSS);

        /*
          읽기와 편집은 종이 크기만 다르다.
          종이 안쪽 단위로 적어야 두 화면에서 같은 크기로 읽힌다.
          (px 로 적으면 작은 종이에서만 글자가 커 보인다)
        */
        assertThat(design(css, "/* 날짜 라벨:"))
                .contains("font-size: calc(11 * var(--diary-page-unit));");
        assertThat(design(css, "/* 제목 라벨:"))
                .contains("font-size: calc(13 * var(--diary-page-unit));");
        // 떡메지들이 나눠 쓰는 글자 규칙. 묶음의 마지막 줄로 찾는다
        assertThat(rule(css, ".diary-note-memo-todo .diary-note-text"))
                .contains("font-size: calc(12 * var(--diary-page-unit));");
        // NOTE 규칙 어디에도 px 글자 크기를 못 박아 두지 않는다
        assertThat(noteStyles(css)).doesNotContain("font-size: 1").doesNotContain("px;");
    }

    @Test
    void longWordsFoldInsteadOfSpillingOutOfThePaper() throws IOException {
        String css = Files.readString(DIARY_CSS);

        assertThat(rule(css, ".diary-note-surface"))
                // 마지막 방어. 글이 아무리 길어도 종이 밖으로 나가지 않는다
                .contains("overflow: hidden;");
        assertThat(rule(css, ".diary-note-text"))
                .contains("min-width: 0;")
                .contains("overflow-wrap: break-word;");
    }

    @Test
    void aMemoKeepsTheLineBreaksThatWereTyped() throws IOException {
        assertThat(rule(Files.readString(DIARY_CSS),
                ".diary-note-memo-todo .diary-note-text"))
                .contains("white-space: pre-wrap;");
    }

    @Test
    void aNoteIsTakenOffLikeAPhotoOrASticker() throws IOException {
        String note = noteFigure();

        // 사진·스티커와 같은 액션 줄. 물어보는 방식도 같다
        assertThat(note)
                .contains("diary-layer-actions")
                .contains("data-layer-direction=\"BACKWARD\"")
                .contains("data-layer-direction=\"FORWARD\"")
                .contains("th:data-delete-url")
                .contains("/note/delete")
                .contains("data-delete-confirm=");
    }

    @Test
    void aNoteIsNotTypeableUntilItIsOpenedForEditing() throws IOException {
        String note = noteFigure();

        /*
          처음부터 고쳐 쓸 수 있는 상태로 그리지 않는다.
          두 번 눌러 열었을 때만 contenteditable 이 붙는다(diary-note-text.js).
          입력칸(textarea)으로 바꿔 끼우지도 않는다 — 종이 위의 글 그대로다.
        */
        assertThat(note)
                .doesNotContain("contenteditable=")
                .doesNotContain("<textarea");
        // 글쓰기 주소는 편집 화면에서만 실린다
        assertThat(note).contains("th:data-text-url=\"${editMode}");
    }

    @Test
    void theQuietPaperLookIsKeptWithoutNoisyTexture() throws IOException {
        String css = Files.readString(DIARY_CSS);

        String notes = noteStyles(css);

        // 움직이거나 번지는 효과는 두지 않는다
        assertThat(notes).doesNotContain("animation").doesNotContain("filter:");
        /*
          줄·모눈·도트 같은 무늬는 쓰지만 간격을 종이 단위로 잡는다.
          px 로 못 박으면 종이가 작아질 때 촘촘해져 화면에서 지글거린다.
        */
        assertThat(notes).contains("repeating-linear-gradient");
        assertThat(notes)
                .doesNotContain("transparent 1px 4px")
                .doesNotContain("transparent 1px 6px");
        // 그늘도 종이 한 장이 놓인 정도로만 둔다
        assertThat(rule(css, ".diary-note-memo-todo .diary-note-surface"))
                .contains("box-shadow: 0 1px 2px rgba(63, 52, 38, 0.06);");
    }

    @Test
    void photosAndStickersAreDrawnExactlyAsBefore() throws IOException {
        String template = Files.readString(DETAIL_HTML);

        // 사진 액자와 스티커는 예전 구조 그대로다
        assertThat(template)
                .contains("class=\"diary-canvas-item diary-canvas-photo diary-photo\"")
                .contains("class=\"diary-canvas-item diary-canvas-sticker diary-sticker\"")
                .contains("th:data-sticker-kind=\"${element.stickerKind}\"");
        // 사진·스티커의 지우기 액션도 그대로 남아 있다
        assertThat(template).contains("photo/delete").contains("sticker/delete");
    }

    /** NOTE 를 그리는 figure 한 덩어리. */
    private String noteFigure() throws IOException {
        String template = Files.readString(DETAIL_HTML);
        int start = template.indexOf("class=\"diary-canvas-item diary-note\"");
        assertThat(start).as("NOTE figure 를 찾지 못했습니다").isNotNegative();
        return template.substring(start, template.indexOf("</figure>", start));
    }

    /**
     * 모양 하나를 정하는 규칙 묶음. 앞에 붙은 설명에서 다음 설명 전까지를 본다.
     * (선택자가 여럿과 묶여 있어도 이 디자인의 것만 잘린다)
     */
    private String design(String css, String comment) {
        int start = css.indexOf(comment);
        assertThat(start).as("설명을 찾지 못했습니다: " + comment).isNotNegative();
        int end = css.indexOf("\n/*", start + comment.length());
        assertThat(end).as("다음 설명을 찾지 못했습니다: " + comment).isGreaterThan(start);
        return css.substring(start, end);
    }

    /** NOTE 규칙 전체. 여기 밖의 다이어리 규칙은 보지 않는다. */
    private String noteStyles(String css) {
        int start = css.indexOf("/* ===== 라벨 / 떡메모지 (NOTE) =====");
        assertThat(start).as("NOTE 규칙 묶음을 찾지 못했습니다").isNotNegative();
        return css.substring(start, css.indexOf("/* 선택했을 때만 보이는 작은 조절점", start));
    }

    /** 여는 중괄호까지 포함해 그 규칙 한 덩어리만 잘라 본다. */
    private String rule(String css, String selector) {
        int start = css.indexOf("\n" + selector + " {");
        assertThat(start).as("규칙을 찾지 못했습니다: " + selector).isNotNegative();
        return css.substring(start, css.indexOf('}', start));
    }
}
