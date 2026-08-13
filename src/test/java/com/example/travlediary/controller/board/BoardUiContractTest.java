package com.example.travlediary.controller.board;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BoardUiContractTest {

    @Test
    void ajaxAcceptsBookmarkSortAndTemplateUsesSecurityAuthorization() throws IOException {
        String javascript = resource("/static/js/board-ajax.js");
        String combobox = resource("/static/js/country-combobox.js");
        String template = resource("/templates/board/list.html");
        String css = resource("/static/css/board-list.css");

        assertThat(javascript)
                .contains("['latest', 'oldest', 'views', 'comments', 'bookmarks']")
                .contains("updateBoardSortState(activeSort)")
                .contains("new URLSearchParams(window.location.search)")
                .contains("window.history.pushState")
                .contains("window.addEventListener('popstate'")
                .contains("params.set('scope', 'overseas')")
                .contains("function changeBoardScope(event, scope)")
                .contains("const params = new URLSearchParams(window.location.search)")
                .contains("params.set('page', '1')")
                .contains("params.delete('countryId')");
        assertThat(combobox)
                .contains("function extractHangulInitials(value)")
                .contains("event.key === 'ArrowDown'")
                .contains("event.key === 'ArrowUp'")
                .contains("event.key === 'Enter'")
                .contains("event.key === 'Escape'");
        assertThat(template)
                .contains("data-sort=\"bookmarks\"")
                .contains("aria-pressed=${sort == 'bookmarks'}")
                .contains("sec:authorize=\"isAuthenticated()\"")
                .contains("class=\"board-country-filter\"")
                .contains("scope='all'")
                .contains("scope='domestic'")
                .contains("scope='overseas'")
                .contains("onclick=\"changeBoardScope(event, 'domestic')\"")
                .contains("id=\"board-country-input\"")
                .contains("role=\"combobox\"")
                .contains("id=\"board-country-listbox\"")
                .contains("overseasCourseCountries")
                .contains("/js/country-combobox.js")
                .doesNotContain("<select id=\"board-country-select\"");
        assertThat(css)
                .contains(".board-country-option-list")
                .contains("max-height: 220px")
                .contains("overflow-y: auto")
                .contains(".board-scope-link--all.active")
                .contains(".board-scope-link--domestic.active")
                .contains(".board-scope-link--overseas.active")
                .contains(".board-sort-button.active::after");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
