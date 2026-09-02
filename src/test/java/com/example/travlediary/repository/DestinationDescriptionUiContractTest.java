package com.example.travlediary.repository;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationDescriptionUiContractTest {

    @Test
    void publicDescriptionUsesEscapedParagraphsWithoutChangingItsContainerWidth() throws IOException {
        Document detail = Jsoup.parse(resource("/templates/destination/detail.html"));
        Element description = detail.selectFirst(".destination-description");
        String css = resource("/static/css/detail.css");

        assertThat(description).isNotNull();
        assertThat(description.select("p")).hasSize(1);
        assertThat(description.selectFirst("p").attr("th:each")).isEqualTo("paragraph : ${descriptionParagraphs}");
        assertThat(description.selectFirst("span").attr("th:text")).isEqualTo("${paragraph}");
        assertThat(description.select("[th\\:utext]")).isEmpty();

        int descriptionCssStart = css.indexOf(".destination-description");
        int descriptionCssEnd = css.indexOf("/* 메타 정보 */", descriptionCssStart);
        String descriptionCss = css.substring(descriptionCssStart, descriptionCssEnd);
        assertThat(descriptionCss)
                .contains("line-height: 1.72;")
                .contains("margin-top: 0.9em;")
                .doesNotContain("max-width")
                .doesNotContain("white-space: pre-line")
                .doesNotContain("word-break: keep-all");
    }

    @Test
    void createAndEditFormsKeepTextareasAndOnlyShowTheParagraphHelp() throws IOException {
        for (String formPath : new String[]{
                "/templates/admin/destinations/create.html",
                "/templates/admin/destinations/edit.html"}) {
            String form = resource(formPath);
            Document page = Jsoup.parse(form);

            assertThat(page.select("textarea.is-description")).hasSize(2);
            assertThat(page.text()).contains("문단을 나누려면 한 줄을 비워주세요.");
            assertThat(page.select("[data-description-preview], [data-description-preview-toggle], [data-description-preview-content]")).isEmpty();
            assertThat(form).doesNotContain("admin-destination-description-preview.js");
        }
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
