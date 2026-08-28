package com.example.travlediary.controller.diary;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(css).contains("line-height: calc(30 * var(--diary-page-unit));");
        assertThat(rule(css, ".diary-sheet-date"))
                .contains("font-size: calc(13 * var(--diary-page-unit));");
        assertThat(rule(css, ".diary-sheet-number"))
                .contains("font-size: calc(12 * var(--diary-page-unit));");
        assertThat(rule(css, ".diary-sheet-body"))
                .contains("padding-top: calc(10 * var(--diary-page-unit));");
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
        assertThat(read).contains("width: min(1660px, calc(100% - 40px));");
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
