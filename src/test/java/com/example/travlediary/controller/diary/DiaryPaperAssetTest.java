package com.example.travlediary.controller.diary;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 종이 질감 / 리본 책갈피 / 목록 표지는 모두 diary.css 안에서만 만든다.
 * (외부 texture 이미지를 쓰지 않고, 종이 기본색은 나중에 바꿀 수 있게 변수로 분리한다)
 */
class DiaryPaperAssetTest {

    private static final Path DIARY_CSS = Path.of("src/main/resources/static/css/diary.css");

    @Test
    void paperBaseColorIsAVariableSoPaperColorCanBeAddedLater() throws IOException {
        String css = Files.readString(DIARY_CSS);

        // paper_color 가 없는 페이지의 기본 종이색은 흰 종이에 가깝다
        assertThat(css).contains("--diary-paper-color: #fdfdfa;");
        // 종이 질감은 기본색을 알지 못한 채 빛/그늘만 얹는다
        assertThat(css).contains("--diary-paper-grain:");
        assertThat(css).contains("--diary-paper-edge:");
        assertThat(css).contains("--diary-paper-texture: var(--diary-paper-edge), var(--diary-paper-grain);");
        assertThat(css).contains("background: var(--diary-paper-texture), var(--diary-paper-color);");
    }

    @Test
    void paperTextureIsMadeWithGradientsOnlyAndNoExternalImage() throws IOException {
        String css = Files.readString(DIARY_CSS);
        String texture = css.substring(css.indexOf("--diary-paper-grain:"),
                css.indexOf("--diary-paper-texture:"));

        assertThat(texture).contains("repeating-linear-gradient").contains("radial-gradient");
        /*
          종이 질감에 외부 이미지를 쓰지 않는다.
          (라벨/메모지의 장식이나 스프링 코일은 그림을 쓰지만, 그것들은 종이 위에 얹는
           별개의 층이다. 종이 자체와 그 무늬는 그리기만으로 만든다)
        */
        assertThat(texture).doesNotContain("url(");
        assertThat(css.substring(css.indexOf("--diary-paper-grain:"),
                        css.indexOf(".diary-sheet-left {")))
                .doesNotContain("url(");
    }

    @Test
    void everyBackgroundTypeKeepsItsPatternAboveThePaperTexture() throws IOException {
        String css = Files.readString(DIARY_CSS);

        for (String backgroundType : new String[]{"plain", "lined", "grid", "dot"}) {
            String rule = rule(css, ".diary-sheet-bg-" + backgroundType);

            assertThat(rule)
                    .as("종이 무늬 규칙: " + backgroundType)
                    .contains("var(--diary-paper-texture)")
                    .contains("var(--diary-paper-color)");
            // 무늬 → 질감 → 기본색 순으로 겹쳐야 선/점이 질감에 묻히지 않는다
            assertThat(rule.indexOf("var(--diary-paper-texture)"))
                    .as("무늬가 질감보다 위: " + backgroundType)
                    .isLessThan(rule.indexOf("var(--diary-paper-color)"));
        }
    }

    /** 자유배치 좌표의 기준은 종이 전체다. (머리말 높이만큼 밀린 offset 을 두지 않는다) */
    @Test
    void canvasIsAnchoredToTheWholeSheet() throws IOException {
        String css = Files.readString(DIARY_CSS);
        String canvas = rule(css, ".diary-canvas");

        assertThat(canvas).contains("inset: 0;");
        assertThat(canvas).contains("pointer-events: none;");
        // 읽기/편집을 맞추려고 고정 px 보정을 넣지 않는다
        assertThat(canvas).doesNotContain("top:").doesNotContain("margin");
    }

    /**
     * 일반 노트의 펼침 한가운데에는 아무 장식도 없다.
     *
     * <p>예전에는 책등 그늘과 실크 리본, 두 장의 접힘 그림자가 겹쳐 펼침 가운데에
     * 세로 줄 하나가 그어진 것처럼 보였다. 지금은 칸 자체를 0 으로 줄여 두 장이 이어진다.
     */
    @Test
    void theClassicSpreadHasNothingDownItsMiddle() throws IOException {
        String css = Files.readString(DIARY_CSS);

        assertThat(css).doesNotContain(".diary-book-ribbon");
        assertThat(rule(css, ".diary-book-spread.diary-book-classic"))
                .contains("grid-template-columns: minmax(0, 1fr) 0 minmax(0, 1fr);");
        // 접힘 그림자도 두지 않는다. (종이 모서리 둥글기는 그대로)
        assertThat(rule(css, ".diary-sheet-left"))
                .contains("border-radius: 12px 0 0 12px;")
                .doesNotContain("box-shadow");
        assertThat(rule(css, ".diary-sheet-right")).doesNotContain("box-shadow");
        // 제본을 보여야 하는 스프링 노트만 그 칸에 그늘을 남긴다
        assertThat(rule(css, ".diary-book-spiral .diary-book-gutter")).contains("linear-gradient");
    }

    @Test
    void listCoverGetsItsMaterialFromVariablesSoCoverStylesCanBeAddedLater()
            throws IOException {
        String css = Files.readString(DIARY_CSS);

        assertThat(css).contains("--diary-cover-color:");
        assertThat(css).contains("--diary-cover-grain:");

        String cover = rule(css, ".diary-book-cover");
        assertThat(cover).contains("var(--diary-cover-grain)").contains("var(--diary-cover-color)");
        // 3:4 비율과 표지 두께/페이지 단면
        assertThat(cover).contains("aspect-ratio: 3 / 4;");
        assertThat(cover).contains("box-shadow:");
        // 앞표지와 책등 사이의 홈
        assertThat(css).contains(".diary-book-spine::after {");
    }

    @Test
    void coverImageStillShowsTheWholePicture() throws IOException {
        String css = Files.readString(DIARY_CSS);
        String image = rule(css, ".diary-book-image");

        // 표지 사진은 잘리지 않는다 (cover 로 되돌리지 않는다)
        assertThat(image).contains("object-fit: contain;").doesNotContain("object-fit: cover;");
        // 표지에 붙여 둔 사진처럼 사진 테두리에만 그림자를 준다
        assertThat(image).contains("drop-shadow");
    }

    @Test
    void hoverStaysSubtle() throws IOException {
        String css = Files.readString(DIARY_CSS);
        String hover = rule(css, ".diary-book:hover .diary-book-cover");

        assertThat(hover).containsPattern("rotate\\(-?[01](\\.\\d+)?deg\\)");
        assertThat(hover).doesNotContain("perspective").doesNotContain("rotateY");
    }

    /** 선택자가 줄 맨 앞에 오는 규칙 하나만 잘라 읽는다. (같은 이름이 들어간 다른 규칙과 섞이지 않게) */
    private String rule(String css, String selector) {
        int start = css.indexOf("\n" + selector + " {");
        assertThat(start).as("규칙을 찾지 못했습니다: " + selector).isPositive();
        return css.substring(start, css.indexOf("\n}", start));
    }
}
