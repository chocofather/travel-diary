package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시글 상세 하단 액션 영역 계약.
 * footer 에는 목록 이동만 남고 작성자 액션(⋯)과 관리자 조치(관리 ▾)는 모두 제목 우측 메뉴에 있으며,
 * 숨김 사유는 상시 노출 없이 모달에서만 받는다.
 */
class PostDetailFooterUiContractTest {

    @Test
    void footerKeepsOnlyTheListLinkWhileOwnerAndAdminActionsMoveToHeaderMenus() throws IOException {
        String detail = readFile("src/main/resources/templates/post/detail.html");
        String footer = between(detail, "<footer class=\"post-detail-footer\">", "</footer>");

        // footer 에는 목록 이동만 남는다. 수정/삭제/숨김 중복 버튼이 없어야 한다.
        assertThat(footer).contains("back-to-list");
        assertThat(footer)
                .doesNotContain("post-detail-actions")
                .doesNotContain("post-edit-button")
                .doesNotContain("post-delete-button")
                .doesNotContain("post-hide-button")
                .doesNotContain("data-post-hide-open");

        // 작성자 액션은 북마크 옆의 compact 메뉴에서만 렌더링된다.
        String header = between(detail, "<header class=\"post-detail-header\">", "</header>");
        assertThat(header)
                .contains("post-header-actions")
                .contains("post-owner-menu")
                .contains("th:if=\"${post.myPost}\"")
                .contains("post-edit-button")
                .contains("post-delete-button")
                .contains("onsubmit=\"return confirm('");

        // 관리자 조치는 작성자 메뉴와 분리된 ADMIN 전용 드롭다운에 있다.
        String adminMenu = between(header, "<details class=\"post-owner-menu post-admin-menu\"", "</details>");
        assertThat(adminMenu)
                .contains("data-owner-menu")
                .contains("sec:authorize=\"hasRole('ADMIN')\"")
                .contains("post-admin-toggle")
                .contains("post-hide-button")
                .contains("data-post-hide-open")
                // 작성자 전용 액션은 관리 메뉴로 새지 않는다.
                .doesNotContain("post-edit-button")
                .doesNotContain("post-delete-button");
        // 작성자 ⋯ 는 본인 글에만, 관리 ▾ 는 ADMIN 에게만 걸린다(권한 조건을 섞지 않는다).
        String ownerMenu = between(header, "<details class=\"post-owner-menu\" data-owner-menu", "</details>");
        assertThat(ownerMenu)
                .contains("th:if=\"${post.myPost}\"")
                .doesNotContain("hasRole('ADMIN')");

        // 상시 노출되던 관리자 조치 패널과 사유 입력창은 사라졌다
        assertThat(footer)
                .doesNotContain("post-moderation-panel")
                .doesNotContain("content-moderation-reason");
        assertThat(detail)
                .doesNotContain("post-moderation-panel")
                .doesNotContain("content-moderation-form");
    }

    @Test
    void hideReasonIsAskedInAnAdminOnlyModalKeepingTheExistingRequest() throws IOException {
        String detail = readFile("src/main/resources/templates/post/detail.html");
        String modal = between(detail, "id=\"post-hide-modal\"", "<section id=\"post-comments\"");

        // 모달은 ADMIN 에게만 렌더링되고 기본은 닫혀 있다
        assertThat(between(detail, "<div sec:authorize=\"hasRole('ADMIN')\" id=\"post-hide-modal\"", ">"))
                .contains("class=\"post-hide-modal\"")
                .contains("hidden");
        // 서버 요청 형식(엔드포인트/필드명)은 그대로 쓴다
        assertThat(modal)
                .contains("/admin/contents/POST/{id}/hide")
                .contains("method=\"post\"")
                .contains("name=\"redirect\"")
                .contains("name=\"reason\"")
                .contains("required")
                .contains("data-post-hide-close");

        String script = resource("/static/js/post-hide-modal.js");
        assertThat(script)
                .contains("[data-post-hide-open]")
                .contains("modal.hidden = false")
                .contains("reason.focus()")
                // 닫을 때 입력값 초기화
                .contains("reason.value = ''")
                // backdrop 클릭 / 취소·X 버튼 / Escape 로 닫는다 (내부 클릭은 제외)
                .contains("event.target === modal")
                .contains("event.target.closest('[data-post-hide-close]')")
                .contains("event.key === 'Escape'")
                // 공백만 입력하면 전송하지 않는다
                .contains("if (!reason.value.trim())")
                .contains("event.preventDefault()");
    }

    @Test
    void bodyHasNoFixedHeightAndActionsKeepALowerEmphasis() throws IOException {
        String css = resource("/static/css/post-detail.css");

        // 본문은 최소 높이만 두고(고정 height 금지) 내용에 따라 늘어난다
        assertThat(between(css, ".post-content {", "}"))
                .contains("min-height: 190px")
                .doesNotContain("\n    height:");
        // 목록으로는 solid 버튼이 아니라 가벼운 텍스트 링크다
        assertThat(between(css, "/* 목록으로: solid 버튼 대신 가벼운 텍스트 링크 */", "}"))
                .doesNotContain("background")
                .contains("text-decoration: none");
        // 숨김 모달은 compact 한 폭에 흰 배경이고 기본은 숨겨져 있다
        assertThat(between(css, ".post-hide-dialog {", "}"))
                .contains("max-width: 460px")
                .contains("background: #fff");
        assertThat(css).contains(".post-hide-modal[hidden]");
        // 수정/삭제/숨김은 흰 배경의 작은 보조 버튼이다
        assertThat(between(css, ".post-edit-button,\n.post-delete-button,\n.post-hide-button {", "}"))
                .contains("background: #fff")
                .contains("padding: 6px 12px");
        // 숨김은 삭제와 구분되는 amber 계열 outline
        assertThat(between(css, "/* 숨김(관리자): 삭제와 구분되는 amber 계열의 낮은 위계 outline */", "}"))
                .contains("color: #b45309")
                .contains("border: 1px solid #ecd9b0");
        // 삭제는 연한 red outline, 수정은 중립 outline
        assertThat(between(css, "/* 삭제: 강한 solid 대신 연한 red outline */", "}"))
                .contains("color: #b42318")
                .contains("border: 1px solid #e8c4c0");
        assertThat(between(css, "/* 수정: 중립 outline */", "}"))
                .contains("color: #4b5563")
                .contains("border: 1px solid #d5d7db");
        // 여행코스 상세가 쓰는 공용 관리자 조치 스타일은 그대로 둔다
        assertThat(resource("/static/css/content-comment.css"))
                .contains(".content-moderation-form")
                .contains("background: #b04a42");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readFile(String path) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)),
                StandardCharsets.UTF_8);
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
