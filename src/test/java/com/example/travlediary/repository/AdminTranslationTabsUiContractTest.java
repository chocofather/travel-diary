package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 번역 언어 탭 UI 규약.
 *
 * <p>탭은 공통이고 입력 칸은 유형마다 다르다. 감춘 언어의 값도 폼에 그대로 실려야 한다.
 */
class AdminTranslationTabsUiContractTest {

    private static final String FRAGMENT =
            "/templates/admin/destinations/fragments/translation-tabs.html";

    @Test
    void theFragmentRendersOneTabAndOnePanelPerLanguageWithTheFirstOneOpen() throws IOException {
        String fragment = resource(FRAGMENT);

        assertThat(fragment)
                .contains("data-translation-tabs")
                .contains("role=\"tablist\"")
                .contains("data-translation-tab=${translation.languageCode}")
                .contains("data-translation-panel=${translation.languageCode}")
                .contains("role=\"tabpanel\"")
                // 첫 번째(영어) 탭만 열린 채로 그려진다
                .contains("th:classappend=\"${slot.first} ? ' is-active'\"")
                .contains("aria-selected=${slot.first}")
                .contains("th:hidden=\"${!slot.first}\"");
        // 탭과 패널은 같은 슬롯 목록을 돈다 (언어 수를 화면에 박지 않는다)
        for (String slots : new String[]{"restaurantInfoTranslations", "attractionInfoTranslations",
                "accommodationInfoTranslations", "activityInfoTranslations"}) {
            assertThat(count(fragment, "th:each=\"translation, slot : *{" + slots + "}\""))
                    .as(slots).isEqualTo(2);
        }
        // 감출 때 입력을 비활성화하지 않는다 (disabled 면 저장에서 빠진다)
        assertThat(fragment).doesNotContain("disabled");
    }

    @Test
    void theScriptSwitchesTabsWithoutClearingOtherLanguages() throws IOException {
        String script = resource("/static/js/admin-translation-tabs.js");

        assertThat(script)
                .contains("[data-translation-tabs]")
                .contains("[data-translation-tab]")
                .contains("[data-translation-panel]")
                .contains("panel.hidden = panel.dataset.translationPanel !== languageCode")
                .contains("tab.classList.toggle(\"is-active\", active)")
                .contains("tab.setAttribute(\"aria-selected\", String(active))")
                // 처음에는 첫 번째 언어를 연다
                .contains("activate(tabs[0].dataset.translationTab)")
                // 값을 지우거나 입력을 막지 않는다
                .doesNotContain(".value = \"\"")
                .doesNotContain("disabled = true")
                .doesNotContain("remove()");
    }

    @Test
    void bothAdminFormsLoadTheTabScript() throws IOException {
        for (String path : new String[]{
                "/templates/admin/destinations/create.html",
                "/templates/admin/destinations/edit.html"}) {
            assertThat(resource(path)).as(path).contains("/js/admin-translation-tabs.js");
        }
    }

    @Test
    void theTabStyleStaysCompactAndSurvivesNarrowScreens() throws IOException {
        String css = resource("/static/css/destination-create.css");
        String tablist = between(css, ".admin-translation-tablist {", "}");

        assertThat(tablist)
                .contains("display: flex")
                .contains("flex-wrap: wrap")
                .contains("overflow-x: auto");
        assertThat(css)
                .contains(".admin-translation-tab.is-active")
                .contains(".admin-translation-panel[hidden]");
    }

    private int count(String source, String token) {
        return source.split(Pattern.quote(token), -1).length - 1;
    }

    private String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertThat(from).as("start %s", start).isNotNegative();
        int to = source.indexOf(end, from);
        assertThat(to).as("end %s", end).isNotNegative();
        return source.substring(from, to);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
