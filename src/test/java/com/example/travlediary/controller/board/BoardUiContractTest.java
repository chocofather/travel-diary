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
        String template = resource("/templates/board/list.html");

        assertThat(javascript)
                .contains("['latest', 'oldest', 'views', 'comments', 'bookmarks']")
                .contains("updateBoardSortState(activeSort)");
        assertThat(template)
                .contains("data-sort=\"bookmarks\"")
                .contains("aria-pressed=${sort == 'bookmarks'}")
                .contains("sec:authorize=\"isAuthenticated()\"");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
