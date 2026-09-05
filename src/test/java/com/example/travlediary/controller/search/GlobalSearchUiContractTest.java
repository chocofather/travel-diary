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
    void theHeaderSearchStartsClosedAndEmptyOnTheResultPage() throws IOException {
        var input = Jsoup.parse(resource("/templates/fragments/header.html"))
                .selectFirst("form#search-form input[name=q]");
        String script = resource("/static/js/main.js");

        // 헤더 입력칸은 지난 검색어를 되살리지 않는다. q 는 가운데 검색창이 들고 있다.
        assertThat(input).isNotNull();
        assertThat(input.hasAttr("th:value")).isFalse();
        assertThat(input.attr("value")).isBlank();
        // 결과 페이지에서는 열림 상태와 값이 남지 않도록 초기화한다.
        assertThat(script)
                .contains("window.location.pathname === '/search'")
                .contains("setSearchOpen(false);");
    }

    @Test
    void theResultPageKeepsItsOwnSearchInputPreloadedWithTheQuery() throws IOException {
        var form = Jsoup.parse(resource("/templates/search.html"))
                .selectFirst("form.global-search-form");

        assertThat(form).isNotNull();
        assertThat(form.attr("action")).isEqualTo("/search");
        var input = form.selectFirst("input[name=q]#global-search-input");
        assertThat(input).isNotNull();
        // 가운데 검색창은 계속 q 를 채워 두고 재검색을 받는다.
        assertThat(input.attr("th:value")).contains("searchPage.query");
    }

    @Test
    void theClearButtonHasAWideHitAreaThatDoesNotOverlapTheSubmitButton() throws IOException {
        var form = Jsoup.parse(resource("/templates/fragments/header.html"))
                .selectFirst("form#search-form");
        String style = resource("/static/css/style.css");

        var clear = form.selectFirst("button.search-clear#header-search-clear");
        assertThat(clear).isNotNull();
        // 검색을 실행하는 버튼이 아니다. 값이 있을 때만 나타난다.
        assertThat(clear.attr("type")).isEqualTo("button");
        assertThat(clear.hasAttr("hidden")).isTrue();
        assertThat(clear.attr("th:aria-label")).isEqualTo("#{header.search.clear}");

        assertThat(style)
                // 보이는 X 는 12px 그대로 두고 클릭 영역만 36x36 으로 넓힌다.
                .contains("#search-form .search-clear{")
                .contains("width:36px")
                .contains("height:36px")
                .contains("cursor:pointer")
                .contains("align-items:center")
                .contains("justify-content:center")
                .contains(".search-clear-icon{")
                .contains("width:12px")
                // 검색 버튼(right:1px, 38px) 바로 왼쪽이라 서로 겹치지 않는다.
                .contains("right:39px")
                .contains("padding:9px 78px 9px 12px")
                // 클릭 영역이 좁은 브라우저 기본 X 는 쓰지 않는다.
                .contains("::-webkit-search-cancel-button");
    }

    @Test
    void clearingTheHeaderSearchNeverRunsASearch() throws IOException {
        String script = resource("/static/js/main.js");

        assertThat(script)
                .contains("clearBtn?.addEventListener('click'")
                .contains("e.preventDefault()")
                .contains("e.stopPropagation()")
                .contains("searchInput.value = ''")
                .contains("searchInput.focus()")
                // 값이 바뀔 때마다 버튼 표시 상태를 즉시 맞춘다.
                .contains("searchInput?.addEventListener('input', syncClearButton)")
                .contains("clearBtn.hidden =");
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
