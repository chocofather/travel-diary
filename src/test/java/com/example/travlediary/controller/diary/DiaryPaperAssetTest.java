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

        assertThat(css).contains("--diary-paper-color: #fdfaf3;");
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
        // texture 용 외부 이미지를 요청하지 않는다
        assertThat(css).doesNotContain("url(");
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

    @Test
    void ribbonIsDecorationOnlyAndCannotBlockThePaper() throws IOException {
        String css = Files.readString(DIARY_CSS);
        String ribbon = rule(css, ".diary-book-ribbon");

        assertThat(ribbon).contains("pointer-events: none;");
        // 책 높이의 절반보다 길고 전체보다는 짧다
        assertThat(ribbon).containsPattern("height: (5[5-9]|6[0-9]|7[0-5])%;");
        // 실크 줄처럼 아주 얇다 (막대기로 보이면 안 된다)
        assertThat(ribbon).containsPattern("width: [3-7]px;");
        // 편집 한 장에서도 같은 리본을 제본 자리에서 내려 준다
        assertThat(css).contains(".diary-book-ribbon.is-single {");
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
