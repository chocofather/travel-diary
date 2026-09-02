package com.example.travlediary.controller.search;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSearchUiContractTest {

    @Test
    void headerUsesNativeGetFormAndKeepsLoginBehavior() throws IOException {
        String header = resource("/templates/fragments/header.html");
        var document = Jsoup.parse(header);
        var form = document.selectFirst("form#search-form");

        assertThat(form).isNotNull();
        assertThat(form.attr("action")).isEqualTo("/search");
        assertThat(form.attr("method")).isEqualToIgnoringCase("get");
        assertThat(form.select("input[name=q]#header-search-input")).hasSize(1);
        var submit = form.selectFirst("button[type=submit]");
        assertThat(submit).isNotNull();
        assertThat(submit.attr("th:aria-label")).isEqualTo("#{header.search.submit}");
        assertThat(submit.select("img[src='/images/magnify.svg']")).hasSize(1);
        assertThat(form.select("button[type=submit]").text()).isBlank();
        assertThat(document.select("#login-btn[onclick*='/login?redirect=']")).hasSize(1);
    }

    @Test
    void mainScriptOnlyControlsSearchDisclosure() throws IOException {
        String script = resource("/static/js/main.js");

        assertThat(script)
                .contains("form.classList.toggle('open', isOpen)")
                .contains("searchBox.classList.toggle('search-open', isOpen)")
                .contains("aria-expanded")
                .contains("!searchBox.contains(e.target)")
                .doesNotContain("window.location.href = `/search")
                .doesNotContain("form.addEventListener('submit'");
    }

    @Test
    void resultPageKeepsQueryAndTypeInFiltersAndPaginationAndEscapesContent() throws IOException {
        String template = resource("/templates/search.html");

        assertThat(template)
                .contains("name=\"q\"")
                .contains("th:each=\"filter : ${searchTypes}\"")
                .contains("@{/search(q=${searchPage.query},type=${filter.queryValue},page=1)}")
                .contains("@{/search(q=${searchPage.query},type=${searchPage.type},page=${pageNumber})}")
                .contains("th:text=\"${result.summary}\"")
                .contains("result.type == 'destination' or result.type == 'event'")
                .contains("th:alt=\"${result.title}\"")
                .contains("onerror=\"this.remove()\"")
                .contains("|${result.startDate} ~ ${result.endDate}|")
                .doesNotContain("th:utext")
                .doesNotContain("search.js");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
