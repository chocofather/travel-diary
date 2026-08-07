package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ContentBookmarkUiContractTest {

    @Test
    void sharedCssUsesDestinationIconsWithoutDestinationSelectors() throws IOException {
        String css = resource("/static/css/content-bookmark.css");

        assertThat(css)
                .contains("width: 44px")
                .contains("height: 44px")
                .contains("width: 27px")
                .contains("url('/uploads/icons/bookmark.png')")
                .contains(".content-bookmark-button.is-bookmarked .content-bookmark-image")
                .contains("url('/uploads/icons/bookmark2.png')")
                .contains(":focus-visible")
                .contains(":disabled")
                .doesNotContain(".bookmark-icon")
                .doesNotContain("#bookmark-icon");
    }

    @Test
    void contentTemplatesKeepTheExistingJavascriptContract() throws IOException {
        for (String path : new String[]{"/templates/post/detail.html", "/templates/course/detail.html"}) {
            assertThat(resource(path))
                    .contains("/css/content-bookmark.css")
                    .contains("class=\"content-bookmark-button\"")
                    .contains("data-bookmark-url")
                    .contains("data-bookmarked")
                    .contains("class=\"content-bookmark-label\"")
                    .contains("aria-pressed")
                    .contains("aria-hidden=\"true\"")
                    .doesNotContain("class=\"bookmark-icon\"")
                    .doesNotContain("id=\"bookmark-icon\"")
                    .doesNotContain("/js/bookmark.js");
        }
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
