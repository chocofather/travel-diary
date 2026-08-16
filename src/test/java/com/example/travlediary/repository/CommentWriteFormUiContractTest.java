package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 댓글 작성폼 UI 계약.
 * 여행지 상세와 커뮤니티 게시글 상세의 작성폼은 같은 카드형 구조와
 * "글자수 / 사진 / 등록" 배치를 쓴다. (목록·답글·수정 폼은 각 화면 기존 디자인 그대로)
 */
class CommentWriteFormUiContractTest {

    @Test
    void bothWriteFormsShowTheSameLengthCounterOrder() throws IOException {
        String destination = readFile("src/main/resources/templates/destination/detail.html");
        String post = readFile("src/main/resources/templates/post/detail.html");

        // 여행지: 글자수 → 사진 → 등록 순서로 우측 하단에 놓인다
        String destinationControls = between(destination, "<div class=\"comment-controls\">", "</div>");
        assertThat(destinationControls.indexOf("id=\"comment-length\""))
                .isGreaterThanOrEqualTo(0)
                .isLessThan(destinationControls.indexOf("id=\"comment-image-input\""));
        assertThat(destinationControls.indexOf("id=\"comment-image-input\""))
                .isLessThan(destinationControls.indexOf("type=\"submit\""));
        assertThat(between(destination, "<textarea name=\"content\"", ">"))
                .contains("maxlength=\"2000\"");

        // 커뮤니티: 기존 글자수 표시를 그대로 유지한다
        String postActions = between(post, "<div class=\"post-comment-form-actions\">", "</div>");
        assertThat(postActions.indexOf("id=\"post-comment-length\""))
                .isGreaterThanOrEqualTo(0)
                .isLessThan(postActions.indexOf("id=\"post-comment-image-input\""));
        assertThat(postActions.indexOf("id=\"post-comment-image-input\""))
                .isLessThan(postActions.indexOf("type=\"submit\""));
    }

    @Test
    void destinationWriteFormCountsCharactersAndResetsWithTheForm() throws IOException {
        String events = resource("/static/js/comment/events.js");
        String counter = between(events, "function initCommentLengthCounter(form)", "// 댓글 등록 폼");

        assertThat(counter)
                .contains("form.querySelector('#comment-length')")
                .contains("textarea.addEventListener('input', update)")
                // 등록 성공 후 form.reset() 이면 글자수도 0 으로 돌아간다
                .contains("form.addEventListener('reset'");
        assertThat(events).contains("initCommentLengthCounter(form);");
    }

    @Test
    void bothWriteFormsUseTheSameCardAndButtonMetrics() throws IOException {
        String commentCss = resource("/static/css/comment.css");
        String postCss = resource("/static/css/post-detail.css");

        // 카드: 커뮤니티 작성폼이 여행지와 같은 테두리/라운드/여백을 쓴다
        String postCard = between(postCss, ".post-comment-form {", "}");
        String destinationCard = between(commentCss, "#comment-form {", "}");
        for (String rule : new String[]{"gap: 12px", "padding: 16px",
                "border: 1px solid #ddd", "border-radius: 10px"}) {
            assertThat(postCard).as("post card %s", rule).contains(rule);
            assertThat(destinationCard).as("destination card %s", rule).contains(rule);
        }

        // textarea: 높이와 안쪽 여백을 맞춘다
        assertThat(between(commentCss, "#comment-form textarea,", "}"))
                .contains("min-height: 100px")
                .contains("padding: 12px");
        assertThat(between(postCss, ".post-comment-form textarea,", "}"))
                .contains("min-height: 100px")
                .contains("padding: 12px");

        // 등록 버튼: 여행지 작성폼도 primary 초록 버튼을 쓴다
        assertThat(between(commentCss, "#comment-form .submit-btn {", "}"))
                .contains("padding: 7px 13px")
                .contains("#397a63")
                .contains("border-radius: 5px");
        // 사진 버튼 크기/아이콘도 같은 값
        assertThat(between(commentCss, "#comment-form .image-upload-label {", "}"))
                .contains("padding: 6px 12px")
                .contains("border-radius: 5px");
        assertThat(between(commentCss, "#comment-form .image-upload-label img {", "}"))
                .contains("width: 18px");
        assertThat(between(postCss, ".post-comments .image-upload-label {", "}"))
                .contains("padding: 6px 12px")
                .contains("border-radius: 5px");
    }

    @Test
    void replyAndEditFormsKeepTheirCurrentDesign() throws IOException {
        String commentCss = resource("/static/css/comment.css");

        // 작성폼에만 새 버튼 규칙을 적용했다 (답글/수정 폼 기본 스타일은 그대로)
        assertThat(between(commentCss, ".submit-btn {", "}"))
                .contains("background-color: #333 !important")
                .contains("padding: 12px 14px !important");
        assertThat(commentCss).contains(".save-edit-btn {");
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
