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
                // 검색 이동은 여전히 form 의 기본 GET 이 맡는다. JS 가 URL 을 만들지 않는다.
                .doesNotContain("window.location.href = `/search")
                .doesNotContain("form.submit()")
                .doesNotContain("location.href = '/search");
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

    @Test
    void anEmptyHeaderSearchClosesTheBoxInsteadOfAskingForSearchWithNoKeyword()
            throws IOException {
        String script = resource("/static/js/main.js");

        // 열린 검색창의 돋보기는 submit 버튼이라, 빈 검색은 submit 에서 막아야 한다.
        assertThat(script)
                .contains("form.addEventListener('submit'")
                .contains(".trim() !== ''")
                .contains("e.preventDefault()")
                .contains("setSearchOpen(false, true)");
        // 닫힌 상태에서 돋보기를 누르면 예전처럼 열고 focus 를 준다.
        assertThat(script)
                .contains("toggleBtn.addEventListener('click'")
                .contains("setSearchOpen(true)")
                .contains("form.querySelector('input')?.focus()")
                .contains("searchBox.classList.toggle('search-open', isOpen)");
    }

    @Test
    void theOpenHeaderSearchStaysInsideTheRightHandActionsArea() throws IOException {
        String style = resource("/static/css/style.css");

        // 가운데 메뉴(--menu-width) 오른쪽에 남는 자리 밖으로 넓어지지 못하게 막는다.
        assertThat(style)
                .contains("@media (min-width: 1200px)")
                .contains("max-width: calc(50vw - (var(--menu-width) / 2) - 20px)");
        // 자리가 모자라면 언어 선택·프로필이 아니라 검색창만 줄어든다.
        assertThat(style)
                .contains("#search-form{")
                .contains("flex-shrink:1")
                .contains("min-width:0")
                .contains("max-width:100%");
        // 검색창이 열려도 메뉴를 접거나 숨기지 않는다.
        assertThat(style).doesNotContain(".search-open .nav-grid");
    }

    @Test
    void theHeaderSearchEndpointAndQueryParameterAreUnchanged() throws IOException {
        var form = Jsoup.parse(resource("/templates/fragments/header.html"))
                .selectFirst("form#search-form");

        assertThat(form).isNotNull();
        assertThat(form.attr("action")).isEqualTo("/search");
        assertThat(form.attr("method")).isEqualToIgnoringCase("get");
        assertThat(form.selectFirst("input[name=q]")).isNotNull();
        // placeholder·라벨은 계속 messages 에서 온다.
        assertThat(form.selectFirst("input[name=q]").attr("th:placeholder"))
                .isEqualTo("#{header.search.placeholder}");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
