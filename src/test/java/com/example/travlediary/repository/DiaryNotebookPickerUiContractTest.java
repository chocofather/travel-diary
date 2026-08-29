package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 새/수정 폼에서 노트 종류를 고르는 자리.
 *
 * <p>표지와 나란히 놓이지만 서로 다른 값이라, 한 조각을 두 화면이 같이 쓰고
 * 견본은 실제 펼침을 옮겨 그리지 않는다.
 */
class DiaryNotebookPickerUiContractTest {

    private static final Path FRAGMENT =
            Path.of("src/main/resources/templates/diary/notebook-type.html");
    private static final Path DIARY_CSS = Path.of("src/main/resources/static/css/diary.css");

    @Test
    void bothFormsUseTheOnePicker() throws IOException {
        for (String form : new String[]{"new", "edit"}) {
            String template = read(Path.of("src/main/resources/templates/diary/" + form + ".html"));
            assertThat(template).as("%s.html", form)
                    .contains("~{diary/notebook-type :: picker(${notebookTypes}, *{notebookType})}");
            // 표지 고르는 자리 아래에 둔다
            assertThat(template.indexOf("diary/cover-style :: picker")).as("%s.html", form)
                    .isLessThan(template.indexOf("diary/notebook-type :: picker"));
        }
    }

    @Test
    void theChosenTypeComesBackCheckedAndTheFirstOneIsTheFallback() throws IOException {
        String fragment = read(FRAGMENT);

        assertThat(fragment).contains("name=\"notebookType\"");
        // 저장된 값이 있으면 그 값이, 없으면 첫 번째(일반 노트)가 골라진다
        assertThat(fragment).contains("${selected != null and !#strings.isEmpty(selected)}");
        assertThat(fragment).contains("${selected == type.code} : ${stat.first}");
    }

    @Test
    void theSampleIsDrawnHereAndNotCopiedFromTheRealSpread() throws IOException {
        String fragment = read(FRAGMENT);

        // 종이 - 제본 - 종이. 실제 화면의 조각(sheetCanvas 등)을 끌어오지 않는다
        assertThat(fragment)
                .contains("diary-notebook-preview-page")
                .contains("diary-notebook-preview-bind");
        assertThat(fragment)
                .doesNotContain("diary-sheet")
                .doesNotContain("diary-book-spread")
                .doesNotContain("sheetCanvas");
    }

    @Test
    void onlyTheBindingTellsTheTwoTypesApart() throws IOException {
        String css = read(DIARY_CSS);

        // 스프링은 같은 자리에 링이 지나간다. (그림 파일 없이 CSS 로만 그린다)
        String spiral = rule(css, ".diary-notebook-option.is-spiral .diary-notebook-preview-bind");
        assertThat(spiral).contains("radial-gradient");
        // 견본은 종이와 제본만 있는 작은 그림이다
        assertThat(rule(css, ".diary-notebook-preview")).contains("width: 52px;");
    }

    @Test
    void theChoiceIsShownWithABorderNotAnAnimation() throws IOException {
        String css = read(DIARY_CSS);

        String checked = rule(css,
                ".diary-notebook-option:has(.diary-notebook-option-input:checked)");
        assertThat(checked).contains("border-color: #b39a75;");
        // :has() 를 못 쓰는 환경에서도 고른 것이 보인다
        assertThat(css).contains(".diary-notebook-option-input:checked ~ .diary-notebook-preview");
        // 고르는 카드에 움직임이나 큰 그림자를 두지 않는다
        assertThat(rule(css, ".diary-notebook-option")).doesNotContain("transition");
    }

    @Test
    void theTwoCardsStackInsteadOfBreakingOnNarrowScreens() throws IOException {
        String css = read(DIARY_CSS);

        assertThat(rule(css, ".diary-notebook-picker"))
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr));");
        // 480px 아래에서는 한 줄에 하나씩 쌓는다
        String narrow = css.substring(css.indexOf("@media (max-width: 480px) {"));
        narrow = narrow.substring(0, narrow.indexOf("\n}"));
        assertThat(narrow).contains(".diary-notebook-picker")
                .contains("grid-template-columns: minmax(0, 1fr);");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** 여는 중괄호까지 포함해 그 규칙 한 덩어리만 잘라 본다. */
    private String rule(String css, String selector) {
        int start = css.indexOf("\n" + selector + " {");
        assertThat(start).as("규칙을 찾지 못했습니다: " + selector).isNotNegative();
        return css.substring(start, css.indexOf('}', start));
    }
}
