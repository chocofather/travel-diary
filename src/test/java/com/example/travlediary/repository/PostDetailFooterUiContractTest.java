package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시글 상세 하단 액션 영역 계약.
 * 목록 이동 / 작성자 액션 / 관리자 숨김 버튼이 한 줄 footer 에 모이고,
 * 숨김 사유는 상시 노출 없이 모달에서만 받는다.
 */
class PostDetailFooterUiContractTest {

    @Test
    void listLinkAuthorActionsAndHideButtonShareOneRow() throws IOException {
        String detail = readFile("src/main/resources/templates/post/detail.html");
        String footer = between(detail, "<footer class=\"post-detail-footer\">", "</footer>");

        // 한 줄 footer: 왼쪽 목록 이동 → 오른쪽 액션
        assertThat(footer.indexOf("back-to-list"))
                .isGreaterThanOrEqualTo(0)
                .isLessThan(footer.indexOf("post-detail-actions"));

        // 작성자 액션(본인 글)과 관리자 숨김 버튼(ADMIN)이 같은 줄에 있다
        String actions = between(footer, "<div class=\"post-detail-actions\">", "</div>\n        </div>");
        assertThat(actions)
                .contains("th:if=\"${post.myPost}\"")
                .contains("post-edit-button")
                .contains("post-delete-button")
                .contains("sec:authorize=\"hasRole('ADMIN')\"")
                .contains("post-hide-button")
                .contains("data-post-hide-open");

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
