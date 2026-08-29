package com.example.travlediary.controller.diary;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 읽기(2면 펼침)와 편집(1면)은 종이 크기만 다르고 좌표계는 같아야 한다.
 * 자유배치 요소(PHOTO/STICKER)는 종이 기준 상대값으로 놓이므로,
 * 종이 안쪽(여백·글자·줄 간격)도 종이 크기에 비례해야 두 화면의 겹침이 같아진다.
 */
class DiaryPageCoordinateAssetTest {

    private static final Path DIARY_CSS = Path.of("src/main/resources/static/css/diary.css");
    private static final Path DETAIL_HTML =
            Path.of("src/main/resources/templates/diary/detail.html");

    /** 종이 한 장의 비율이 두 화면에서 같아야 세로 % 가 같은 자리를 가리킨다. */
    @Test
    void paperKeepsOneRatioAndOneInnerUnit() throws IOException {
        String css = Files.readString(DIARY_CSS);
        String sheet = rule(css, ".diary-sheet");

        assertThat(sheet).contains("aspect-ratio: 41 / 38;");
        assertThat(sheet).contains("container-type: inline-size;");
        assertThat(sheet).contains("--diary-page-unit:");
        // 종이 자신의 여백은 %(자기 너비 기준)라 어느 화면에서도 같은 비율이다
        assertThat(sheet).contains("padding: 5.2% 5.2% 6.95%;");
    }

    /**
     * 줄 높이 / 줄 그림 / 줄 수가 값 하나씩만 보고 움직인다.
     *
     * <p>예전에는 배경은 종이 높이의 %, 본문은 page-unit 환산값, 줄 수는 또 다른 숫자로
     * 따로 적혀 있어서 한 곳만 고쳐도 서로 어긋났다. 세 곳이 같은 변수를 가리키면
     * 몇 줄을 내려가도, 화면이 좁아져도 함께 움직인다.
     */
    @Test
    void theLineHeightTheRuledLinesAndTheLineCountAllComeFromOneValue() throws IOException {
        String css = Files.readString(DIARY_CSS);
        String sheet = rule(css, ".diary-sheet");
        String body = bodyTextRule(css);

        // 기준값은 종이에 한 번만 적는다
        assertThat(sheet).contains("--diary-line: 4.75cqw;").contains("--diary-lines: 16;");
        // 본문 줄 높이 / 줄 그림 / 글 쓰는 자리 높이가 모두 그 값을 가리킨다
        assertThat(body).contains("line-height: var(--diary-line);");
        assertThat(rule(css, ".diary-sheet-bg-lined .diary-writing-layer"))
                .contains("rgba(150, 128, 96, 0.17) var(--diary-line)");
        assertThat(rule(css, ".diary-writing-layer"))
                .contains("height: calc(var(--diary-lines) * var(--diary-line));");

        /*
          줄 간격에는 최소값이 없어야 한다. --diary-page-unit 에는 글자가 작아지지 않게
          막는 최소값이 들어 있어서, 그 값을 쓰면 좁은 화면에서 줄 높이만 멈춘다.
          (글자 크기는 그 최소값을 그대로 쓴다)
        */
        assertThat(body).contains("font-size: calc(15 * var(--diary-page-unit));");
        assertThat(sheet).contains("--diary-page-unit: max(0.87px, 100cqw / 576);");
    }

    /** 본문 글자를 그리는 규칙 한 덩어리. (읽기/편집이 함께 쓴다) */
    private String bodyTextRule(String css) {
        int start = css.indexOf("\n.diary-editor .ql-editor,");
        assertThat(start).as("본문 규칙을 찾지 못했습니다").isNotNegative();
        return css.substring(start, css.indexOf('}', start));
    }

    /**
     * 종이 안에서는 스크롤하지 않는다. 보이는 줄이 곧 쓸 수 있는 줄이다.
     *
     * <p>글 쓰는 자리를 남는 공간 전부(flex: 1)로 두면 들어가는 줄이 14.13 줄 같은
     * 어중간한 값이 되어 마지막 줄이 반쯤 잘리고, 한 줄만 더 써도 스크롤이 생겼다.
     * 줄 높이의 정수배로 못 박아 마지막 줄까지 온전히 보이게 한다.
     */
    @Test
    void thePaperNeverScrollsInsideAndHoldsAWholeNumberOfLines() throws IOException {
        String css = Files.readString(DIARY_CSS);
        String layer = rule(css, ".diary-writing-layer");
        String body = bodyTextRule(css);

        // 높이를 줄 수로 못 박는다. (남는 자리가 어중간한 반 줄이 되지 않는다)
        assertThat(layer).contains("height: calc(var(--diary-lines) * var(--diary-line));");
        // 남는 자리를 다 차지하지 않는다. 남은 만큼이 종이 아래 여백이 된다
        assertThat(layer).contains("flex: 0 0 auto;").doesNotContain("flex: 1;");
        // px 로 된 최소 높이는 좁은 화면에서 종이를 늘려 줄 간격을 깨뜨린다
        assertThat(layer).doesNotContain("min-height");
        // 본문 자체도 스크롤하지 않는다
        assertThat(body).contains("overflow: hidden;").doesNotContain("overflow-y: auto;");
    }

    /**
     * 줄노트의 줄은 종이 자신의 % 로만 그린다.
     *
     * <p>--diary-page-unit 은 100cqw 로 만들어지는데, cqw 는 자기 자신이 아니라 바깥
     * 컨테이너를 잰다. 종이가 곧 컨테이너라, 종이 자신의 배경에서 그 값을 쓰면
     * 화면 폭이 기준이 되어 줄 간격이 크게 벌어지고 굵은 줄 몇 개만 남는다.
     * (본문처럼 종이 안쪽 요소에서 쓰는 것은 종이를 재므로 정상이다)
     */
    @Test
    void theRuledLinesAreDrawnOnTheWritingAreaNotOnThePaper() throws IOException {
        String css = Files.readString(DIARY_CSS);
        String lined = rule(css, ".diary-sheet-bg-lined");
        String lines = rule(css, ".diary-sheet-bg-lined .diary-writing-layer");

        // 종이 자신은 종이색과 질감만 그린다. (줄은 글 쓰는 자리에)
        assertThat(lined).doesNotContain("repeating-linear-gradient");
        /*
          종이 자신의 속성에서는 --diary-line 을 쓸 수 없다. 그 안의 cqw 가 자기 자신이
          아니라 바깥 컨테이너를 재기 때문이다. 안쪽 요소에서 쓰면 종이를 잰다.
        */
        assertThat(lined).doesNotContain("--diary-line");
        /*
          종이 전체에 한 번에 그리는 되풀이 그라데이션이어야 줄이 또렷하게 남는다.
          타일(background-size)로 잘라 붙이면 이음매에서 얇은 줄이 뭉개져 사라진다.
        */
        assertThat(lines).contains("repeating-linear-gradient(");
        assertThat(lines).doesNotContain("background-size").doesNotContain("repeat,");
        // 줄 하나가 글 한 줄의 아래에 오도록 위에서 아래로 되풀이한다
        assertThat(lines).contains("180deg");
    }

    /**
     * 머리말과 본문 첫 줄 사이 거리는 화면을 보고 맞춰 둔 값이라 그대로 지킨다.
     *
     * <p>머리말 쪽 아래 여백을 늘리면 그만큼 본문 위 여백에서 빼야 본문 첫 줄이
     * 제자리에 남는다. 두 값은 늘 함께 움직인다.
     */
    @Test
    void theHeaderAndTheFirstLineKeepTheDistanceThatWasTunedOnScreen() throws IOException {
        String css = Files.readString(DIARY_CSS);

        double row = unitsIn(rule(css, ".diary-sheet-head-row"), "height");
        double headBottomPadding = unitsIn(rule(css, ".diary-sheet-head"), "padding-bottom");
        double bodyTopPadding = unitsIn(rule(css, ".diary-sheet-body"), "padding-top");

        assertThat(row + headBottomPadding + bodyTopPadding)
                .as("머리말 한 칸 + 아래 여백 + 본문 위 여백의 합")
                .isCloseTo(12.97 + 7 + 10, within(0.1));
    }

    /** 종이가 가득 차면 더 받지 않는다. 넘치는 글은 보이지 않을 뿐 사라지지 않아야 한다. */
    @Test
    void typingStopsAtTheBottomOfThePageInsteadOfScrolling() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/diary-editor.js"));
        String guard = js.substring(js.indexOf("function rejectOverflow"));
        guard = guard.substring(0, guard.indexOf("\n    }"));

        // 넘쳤는지는 실제로 그려진 높이로 판단한다
        assertThat(guard).contains("root.scrollHeight <= root.clientHeight");
        // 넘치게 만든 입력만 직전 상태로 되돌린다
        assertThat(guard).contains("setContents(previousContents, 'silent')");
        // 지우는 쪽은 막지 않는다 (이미 넘쳐 있는 글을 정리할 수 있어야 한다)
        assertThat(guard).contains("getLength() <= previousContents.length()");
        // 되돌린 입력은 저장하지 않는다
        assertThat(js).contains("if (rejectOverflow(page, oldDelta)) return;");
    }

    /** 규칙 한 덩어리에서 그 속성의 page-unit 배수를 읽는다. */
    private double unitsIn(String rule, String property) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(property + ": calc\\(([0-9.]+) \\* var\\(--diary-page-unit\\)\\);")
                .matcher(rule);
        assertThat(matcher.find()).as("%s 를 찾지 못했습니다", property).isTrue();
        return Double.parseDouble(matcher.group(1));
    }

    /**
     * 종이를 담는 상자는 종이와 같은 폭이어야 한다.
     *
     * <p>종이 자신의 여백은 % 라, 자기 폭이 아니라 담고 있는 상자의 폭으로 계산된다.
     * 읽기 모드의 종이는 펼침 그리드의 한 칸에 들어 있어 그 칸의 폭이 곧 종이 폭이지만,
     * 편집 모드의 상자를 넓게 두면 화면 폭이 기준이 되어 여백만 커지고 본문이 아래로 밀린다.
     * (한 장 820px, 상자 1121px 이면 위 여백이 29.95u 가 아니라 40.95u 가 된다)
     */
    @Test
    void thePaperSizesItsOwnMarginsFromItsOwnWidthInBothModes() throws IOException {
        String css = Files.readString(DIARY_CSS);
        String single = rule(css, ".diary-book-single");

        // 상자와 종이가 같은 폭이라 % 여백이 종이 폭 기준이 된다
        assertThat(single).contains("width: min(820px, 100%);");
        assertThat(rule(css, ".diary-book-single .diary-sheet-single"))
                .contains("width: min(820px, 100%);");
        // 읽기 쪽은 그리드 칸이 그 일을 한다. 칸 나눔은 그대로다
        assertThat(rule(css, ".diary-book-spread"))
                .contains("grid-template-columns: minmax(0, 1fr) 26px minmax(0, 1fr);");
        // 종이 자신의 여백 값은 두 화면이 나눠 쓰는 한 벌 그대로다
        assertThat(rule(css, ".diary-sheet")).contains("padding: 5.2% 5.2% 6.95%;");
    }

    /**
     * 본문이 시작하는 자리가 두 화면에서 같아야 첫 글자가 같은 줄 위에 앉는다.
     *
     * <p>그 자리를 정하는 것은 날짜 한 줄의 높이다. 읽기 모드는 거기에 글자(span)만 두는데,
     * 편집 모드의 입력칸이 날짜보다 조금이라도 높으면 그만큼 본문 첫 줄이 아래로 밀린다.
     * 그래서 입력칸의 높이를 맞추려 하지 않고, 아예 줄 높이를 정하지 못하게 못 박는다.
     */
    @Test
    void theHeaderInputNeverDecidesTheHeightOfTheHeaderLine() throws IOException {
        String css = Files.readString(DIARY_CSS);
        String input = rule(css, ".diary-sheet-header-input");
        String header = rule(css, ".diary-sheet-header");

        // 글자 크기만큼만 차지한다. 날짜의 줄 높이(normal)는 늘 이보다 크다
        assertThat(input).contains("height: 1em;").contains("line-height: 1em;");
        // 세로 여백과 테두리는 두지 않는다. 밑줄은 자리를 차지하지 않는 box-shadow 로 그린다
        assertThat(input).contains("padding: 0 calc(4 * var(--diary-page-unit));");
        assertThat(input).contains("border: 0;").doesNotContain("border-bottom:");
        assertThat(rule(css, ".diary-sheet-header-input:hover")).contains("box-shadow: inset 0 -1px 0");
        assertThat(rule(css, ".diary-sheet-header-input:focus")).contains("box-shadow: inset 0 -1px 0");
        // 글자 크기는 읽기 쪽과 같고, 읽기 쪽 줄 높이는 건드리지 않는다
        assertThat(input).contains("font-size: calc(13 * var(--diary-page-unit));");
        assertThat(header).contains("font-size: calc(13 * var(--diary-page-unit));");
        assertThat(header).doesNotContain("line-height");
    }

    /**
     * 문단 여백은 두 화면 모두 우리 파일이 지운다.
     *
     * <p>읽기 쪽만 지워 두면 편집 쪽은 외부 Quill 스타일시트에만 기대게 되고,
     * 그것이 늦거나 막히면 첫 문단이 1em(= 줄 간격의 절반) 내려가 줄과 어긋난다.
     */
    @Test
    void paragraphSpacingIsRemovedOnBothPathsNotJustTheReadingOne() throws IOException {
        String css = Files.readString(DIARY_CSS);

        assertThat(rule(css, ".diary-editor p")).contains("margin: 0;");
        // 읽기 전용으로만 좁혀 두지 않는다
        assertThat(css).doesNotContain(".diary-editor.is-read-only p {");
    }

    /** 편집/읽기 어느 쪽도 종이 안쪽 크기를 px 로 따로 정하지 않는다. */
    @Test
    void neitherModeOverridesPaperMetricsWithFixedPixels() throws IOException {
        String css = Files.readString(DIARY_CSS);

        assertThat(rule(css, ".diary-book-single .diary-sheet-single"))
                .doesNotContain("min-height")
                .doesNotContain("padding:");
        // 종이 크기를 px 로 못 박아 두면 두 화면의 세로 비율이 어긋난다
        assertThat(css).doesNotContain("min-height: 760px;");
        assertThat(css).doesNotContain("min-height: 520px;");
    }

    /** 본문/머리말/쪽번호는 모두 종이 안쪽 단위로 그린다. */
    @Test
    void paperContentsAreDrawnWithThePageUnit() throws IOException {
        String css = Files.readString(DIARY_CSS);

        assertThat(css).contains("font-size: calc(15 * var(--diary-page-unit));");
        assertThat(css).contains("line-height: var(--diary-line);");
        assertThat(rule(css, ".diary-sheet-date"))
                .contains("font-size: calc(13 * var(--diary-page-unit));");
        assertThat(rule(css, ".diary-sheet-number"))
                .contains("font-size: calc(12 * var(--diary-page-unit));");
        assertThat(rule(css, ".diary-sheet-body"))
                .contains("padding-top: calc(4.61 * var(--diary-page-unit));");
    }

    /** 자유배치 층은 두 화면 모두 종이 전체를 덮는 같은 조각을 쓴다. */
    @Test
    void bothModesUseTheSameCanvasFragmentOverTheWholePaper() throws IOException {
        String template = Files.readString(DETAIL_HTML);
        String canvas = rule(Files.readString(DIARY_CSS), ".diary-canvas");

        assertThat(canvas).contains("position: absolute;").contains("inset: 0;");
        // 편집 한 장과 읽기 좌/우 세 곳이 모두 같은 조각(sheetCanvas)을 쓴다
        assertThat(template.split("diary/detail :: sheetCanvas", -1)).hasSize(4);
    }

    /**
     * 읽는 화면의 펼침은 바깥 폭만 넓힌다.
     *
     * <p>종이 비율과 안쪽 단위는 손대지 않으므로, 붙여 둔 사진·스티커·라벨은
     * 제자리·같은 비율로 함께 커진다. 세로가 화면을 넘지 않도록 높이도 함께 본다.
     */
    @Test
    void theReadingSpreadOnlyGrowsItsOuterWidth() throws IOException {
        String css = Files.readString(DIARY_CSS);
        String read = rule(css, ".diary-detail-page.is-read-mode");

        // 넓히는 것은 바깥 폭뿐이다. 종이 자체의 크기를 따로 정하지 않는다
        // 큰 모니터에서는 더 커지지 않는다 (노트북에서는 화면 폭을 그대로 쓴다)
        assertThat(read).contains("width: min(1140px, calc(100% - 40px));");
        assertThat(read).doesNotContain("aspect-ratio").doesNotContain("--diary-page-unit");

        // 두 장을 나란히 펼치는 폭에서만 높이로도 한 번 더 줄인다
        int wide = css.indexOf("@media (min-width: 861px) {");
        assertThat(wide).as("넓은 화면 규칙을 찾지 못했습니다").isNotNegative();
        String tall = css.substring(wide, css.indexOf("\n}", wide));
        assertThat(tall).contains(".diary-detail-page.is-read-mode");
        assertThat(tall).contains("100vh - 200px").contains("100dvh - 200px");
        // dvh 를 모르는 브라우저가 앞줄(vh)을 쓰도록 순서를 지킨다
        assertThat(tall.indexOf("100vh")).isLessThan(tall.indexOf("100dvh"));
        // 좁은 화면(한 장씩 넘겨 보는 쪽)은 화면 폭 그대로다
        assertThat(rule(css, ".diary-detail-page.is-read-mode")).doesNotContain("vh");
    }

    /**
     * 액션 줄은 기울어진 요소가 실제로 차지하는 네모 아래에 놓인다.
     *
     * <p>요소의 아래 모서리에 붙여 두면 각도에 따라 그 모서리가 위로 올라와 본문을 가린다.
     * 사진·스티커·라벨이 모두 같은 줄을 쓰므로 규칙도 한 곳에만 둔다.
     */
    @Test
    void theActionRowSitsUnderWhateverSpaceATiltedElementTakesUp() throws IOException {
        String rule = rule(Files.readString(DIARY_CSS), ".diary-layer-actions");

        // 기준점은 요소의 한가운데다. 아래 모서리가 아니다
        assertThat(rule).contains("top: 50%;").contains("left: 50%;");
        assertThat(rule).doesNotContain("top: 100%;");
        /*
          부모의 기울기를 먼저 되돌린다. 그래야 뒤따르는 이동이 화면 축을 따라가
          줄이 늘 수평이고, 내리는 거리도 px 로만 정하면 된다.
        */
        assertThat(rule)
                .contains("rotate(calc(-1 * var(--diary-item-rotation, 0deg)))")
                .contains("translate(var(--diary-actions-shift, 0px), var(--diary-actions-drop, 0px))")
                .contains("translateX(-50%)");
        assertThat(rule.indexOf("rotate(calc(-1"))
                .as("기울기를 되돌리는 것이 이동보다 먼저다")
                .isLessThan(rule.indexOf("translate(var(--diary-actions-shift"));
    }

    @Test
    void howFarTheRowDropsComesFromTheAngleAndTheSize() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/diary-canvas-drag.js"));
        String layout = js.substring(js.indexOf("function layoutActions(item)"));
        layout = layout.substring(0, layout.indexOf("\n    function select("));

        // 기울어진 네모의 높이(|w·sinθ| + |h·cosθ|) 절반만큼 내리고 사이 간격을 더한다
        assertThat(layout)
                .contains("Math.abs(Math.sin(radians))")
                .contains("Math.abs(Math.cos(radians))")
                .contains("(width * sin + height * cos) / 2 + ACTIONS_GAP");
        // 종이 밖으로 나가려 하면 그만큼만 밀어 넣는다
        assertThat(layout)
                .contains("canvas.clientWidth")
                .contains("--diary-actions-shift");
        // 각도는 지금 화면에 걸린 값을 읽는다. (회전하는 도중에도 맞다)
        assertThat(layout).contains("--diary-item-rotation");
    }

    @Test
    void theRowFollowsWhileMovingResizingAndTurning() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/diary-canvas-drag.js"));

        for (String applier : new String[]{
                "function apply(x, y)", "function applySize(width, height)",
                "function applyRotation(degrees)"}) {
            String body = js.substring(js.indexOf(applier));
            body = body.substring(0, body.indexOf("\n        }"));
            assertThat(body).as("%s", applier).contains("layoutActions(item)");
        }
        // 고른 다음에야 줄의 폭을 잴 수 있다
        String select = js.substring(js.indexOf("function select(item)"));
        assertThat(select.substring(0, select.indexOf("\n    }")))
                .contains("layoutActions(item)");
        // 종이 크기가 달라지면 px 로 잡아 둔 값도 다시 구한다
        assertThat(js).contains("window.addEventListener('resize'");
    }

    @Test
    void turningAnElementItselfWorksTheSameAsBefore() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/diary-canvas-drag.js"));
        String rotation = js.substring(js.indexOf("function applyRotation(degrees)"));
        rotation = rotation.substring(0, rotation.indexOf("\n        }"));

        // 요소에는 여전히 회전만 들어간다. 저장하는 값도 그대로다
        assertThat(rotation).contains("item.style.transform = `rotate(${degrees.toFixed(2)}deg)`");
        assertThat(js).contains("saveRotation");
    }

    /** 여는 중괄호까지 포함해 그 규칙 한 덩어리만 잘라 본다. */
    private String rule(String css, String selector) {
        int start = css.indexOf("\n" + selector + " {");
        assertThat(start).as("규칙을 찾지 못했습니다: " + selector).isNotNegative();
        return css.substring(start, css.indexOf('}', start));
    }
}
