package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UserPostTranslationUiContractTest {

    @Test
    void detailUsesOneOnDemandToggleForTitleAndRichTextContent() throws IOException {
        String detail = resource("/templates/post/detail.html");
        String script = resource("/static/js/user-post-translation.js");

        assertThat(detail)
                .contains("/js/user-post-translation.js")
                .contains("th:if=\"${post.translationAvailable}\"")
                .contains("id=\"user-post-translation-toggle\"")
                .contains("id=\"user-post-title\"")
                .contains("id=\"user-post-content\"")
                .contains("@{/post/{id}/translation(id=${post.id})}");
        assertThat(script)
                .contains("fetch(article.dataset.translationUrl")
                .contains("method: 'GET'")
                .doesNotContain("body:")
                .contains("title.textContent = result.translatedTitle")
                .contains("new DOMParser()")
                .contains("content.replaceChildren")
                .contains("originalTitle", "originalContentNodes")
                .doesNotContain("content.innerHTML = response.translatedContent");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
