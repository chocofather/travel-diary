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

    /** 여는 중괄호까지 포함해 그 규칙 한 덩어리만 잘라 본다. */
    private String rule(String css, String selector) {
        int start = css.indexOf("\n" + selector + " {");
        assertThat(start).as("규칙을 찾지 못했습니다: " + selector).isNotNegative();
        return css.substring(start, css.indexOf('}', start));
    }
}
