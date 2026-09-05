package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 정보 카테고리 폼의 번역 입력 UI 규약.
 *
 * <p>탭 시스템은 기존 공통 스크립트·스타일을 그대로 쓰고, 카테고리 이름 하나만 받는다.
 */
class AdminInfoCategoryTranslationUiContractTest {

    private static final String FRAGMENT =
            "/templates/fragments/admin/info-category-translation-tabs.html";
    private static final String FORM = "/templates/admin/info-categories/form.html";

    @Test
    void theFormReusesTheSharedTabScriptAndStylesInsteadOfItsOwnTabSystem()
            throws IOException {
        String form = resource(FORM);

        assertThat(form)
                .contains("<script src=\"/js/admin-translation-tabs.js\" defer></script>")
                .contains("<link rel=\"stylesheet\" href=\"/css/admin-translation-tabs.css\">")
                .contains("fragments/admin/info-category-translation-tabs")
                .contains(":: infoCategoryTranslations")
                // 화면 전용 탭 시스템을 새로 만들지 않는다
                .doesNotContain("data-info-category-translation-tabs")
                // Quill 은 이 화면에 없다
                .doesNotContain("quill");
    }

    @Test
    void theTranslationBlockSitsInsideTheFormSoBindingSurvives() throws IOException {
        String form = resource(FORM);
        int formStart = form.indexOf("<form th:action=\"${formAction}\"");
        int fragment = form.indexOf("infoCategoryTranslations");
        int formEnd = form.indexOf("</form>");

        assertThat(formStart).isGreaterThanOrEqualTo(0);
        assertThat(fragment).isGreaterThan(formStart);
        assertThat(fragment).isLessThan(formEnd);
    }

    @Test
    void koreanStaysInTheBaseInputAndGetsNoTranslationTab() throws IOException {
        String fragment = resource(FRAGMENT);

        // 0번 슬롯(한국어)은 탭에도 패널에도 그리지 않는다.
        assertThat(count(fragment, "th:unless=\"${slot.first}\"")).isEqualTo(2);
        assertThat(fragment).doesNotContain("translations[0]");
        // 한국어 원본 입력은 그대로 남아 있다.
        assertThat(resource(FORM))
                .contains("id=\"info-category-name\" type=\"text\" th:field=\"*{name}\"");
    }

    @Test
    void everyLanguagePanelCarriesOneNameInputBoundByLanguageCode() throws IOException {
        String fragment = resource(FRAGMENT);

        assertThat(fragment)
                .contains("data-translation-tabs")
                .contains("role=\"tablist\"")
                .contains("data-translation-tab=${translation.languageCode}")
                .contains("data-translation-panel=${translation.languageCode}")
                .contains("role=\"tabpanel\"")
                .contains("th:field=\"*{translations[__${slot.index}__].languageCode}\"")
                .contains("th:field=\"*{translations[__${slot.index}__].name}\"")
                .contains("data-translation-name=${translation.languageCode}")
                .contains("maxlength=\"100\"");
        // 탭과 패널은 같은 슬롯 목록을 돈다 (언어 수를 화면에 박지 않는다)
        assertThat(count(fragment, "th:each=\"translation, slot : *{translations}\"")).isEqualTo(2);
        // 감출 때 입력을 비활성화하지 않는다 (disabled 면 저장에서 빠진다)
        assertThat(fragment).doesNotContain("disabled");
        // 언어 코드를 화면에 하드코딩하지 않는다
        assertThat(fragment).doesNotContain("\"en\"", "\"ja\"", "\"zh-CN\"", "\"zh-TW\"");
    }

    @Test
    void adminCategoryListStillShowsTheKoreanBaseName() throws IOException {
        // 관리자 목록은 locale 과 무관하게 base 이름을 그대로 보여 준다.
        assertThat(resource("/templates/admin/info-categories/list.html"))
                .contains("th:text=\"${category.name}\"")
                .doesNotContain("categoryNames")
                .doesNotContain("translations");
    }

    @Test
    void theSharedTabScriptStillKeepsHiddenLanguagesIntact() throws IOException {
        assertThat(resource("/static/js/admin-translation-tabs.js"))
                .contains("panel.hidden = panel.dataset.translationPanel !== languageCode")
                .doesNotContain(".value = \"\"")
                .doesNotContain("disabled = true")
                .doesNotContain("remove()");
    }

    private int count(String source, String token) {
        int total = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            total++;
            index = source.indexOf(token, index + token.length());
        }
        return total;
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
