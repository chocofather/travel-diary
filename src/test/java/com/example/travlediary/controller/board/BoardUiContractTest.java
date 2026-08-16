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

    @Test
    void writeButtonOpensTheSameTypeChooserOnEveryBoard() throws IOException {
        String template = resource("/templates/board/list.html");
        String modal = resource("/templates/board/write-modal.html");
        String script = resource("/static/js/board-write-modal.js");
        String css = resource("/static/css/board-write-modal.css");

        // 글쓰기 버튼은 게시판 종류와 무관하게 같은 선택 모달만 연다
        assertThat(template)
                .contains("class=\"board-write-btn\" data-board-write-open")
                .contains("~{board/write-modal :: writeModal}")
                .doesNotContain("${boardType == 'course'} ? @{/course/write} : @{/post/write}");
        // 세 종류 모두 기존 작성 route 로 이동한다
        assertThat(modal)
                .contains("th:fragment=\"writeModal\"")
                .contains("sec:authorize=\"isAuthenticated()\"")
                .contains("@{/post/write(postType='QUESTION')}")
                .contains("@{/post/write(postType='TIP')}")
                .contains("@{/course/write}")
                .contains("여행 질문")
                .contains("여행 팁")
                .contains("나의 여행코스");

        assertThat(script)
                .contains("[data-board-write-open]")
                .contains("modal.hidden = false")
                // backdrop / 닫기 버튼 / Escape 로 닫는다 (내부 클릭은 제외)
                .contains("event.target === modal")
                .contains("event.target.closest('[data-board-write-close]')")
                .contains("event.key === 'Escape'");
        assertThat(css)
                .contains(".board-write-modal[hidden]")
                .contains(".board-write-option");
    }

    @Test
    void postWriteFormShowsTheTypeAsABadgeInsteadOfASelect() throws IOException {
        String write = resource("/templates/post/write.html");

        // 카테고리 select 는 사라지고 진입 시 정해진 값이 hidden 으로 그대로 전송된다
        assertThat(write)
                .doesNotContain("id=\"postTypeSelect\"")
                .doesNotContain("<select")
                .contains("<input type=\"hidden\" name=\"postType\" th:value=\"${currentPostType}\">")
                .contains("param.postType[0] == 'TIP'");
        // 제목은 "글쓰기"만, 종류는 배지로 표시하고 종류 변경은 같은 모달을 연다
        assertThat(write)
                .contains("<h1 class=\"write-title\">글쓰기</h1>")
                .contains("class=\"write-type-badge\"")
                .contains("${currentPostType == 'TIP' ? '여행 팁' : '여행 질문'}")
                .contains("class=\"write-type-change\" data-board-write-open")
                .contains("~{board/write-modal :: writeModal}")
                .contains("/js/board-write-modal.js");
        assertThat(resource("/static/css/post-write.css"))
                .contains(".write-type-badge")
                .contains(".write-type-change");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
