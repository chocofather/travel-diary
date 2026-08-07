package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContentCommentUiContractTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void detailPagesLoadNamespacedCommonCommentStyles() throws IOException {
        for (String template : new String[]{
                "templates/destination/detail.html",
                "templates/post/detail.html",
                "templates/course/detail.html"
        }) {
            assertThat(resource(template))
                    .contains("/css/content-comment.css")
                    .contains("content-comments")
                    .contains("content-comment-list");
        }

        String css = resource("static/css/content-comment.css");
        assertThat(css)
                .contains(".content-comments .content-comment-item")
                .contains("grid-template-columns: 40px minmax(0, 1fr)")
                .contains("width: 40px")
                .contains("width: 36px")
                .contains("border-left: 1px solid")
                .contains("flex-wrap: wrap")
                .contains("@media (max-width: 768px)")
                .contains("@media (max-width: 480px)")
                .contains("margin-left: 20px")
                .contains("@media (max-width: 320px)")
                .contains("margin-left: 14px")
                .contains("focus-visible")
                .contains("content-comment-sr-only")
                .doesNotContain("\n.comment-item {")
                .doesNotContain("content: '↳'");
    }

    @Test
    void postAndCourseRenderersKeepApisAndAddUnifiedCommentContract() throws IOException {
        for (String scriptPath : new String[]{"static/js/post-comments.js", "static/js/course-comments.js"}) {
            String script = resource(scriptPath);
            assertThat(script)
                    .contains("writerUserId")
                    .contains("writerProfileImage")
                    .contains("content-comment-card")
                    .contains("content-comment-avatar")
                    .contains("content-comment-actions")
                    .contains("content-comment-replies")
                    .contains("content-comment-mention")
                    .contains("/images/default.png")
                    .contains("fallbackApplied")
                    .contains("/uploads/icons/like.png")
                    .contains("/uploads/icons/like2.png")
                    .contains("aria-hidden")
                    .contains("aria-pressed")
                    .contains("date.getFullYear() % 100")
                    .contains("updatedAt > createdAt")
                    .contains("date.dateTime = comment.createdAt")
                    .contains("replyToCommentId")
                    .contains("replyToNickname")
                    .contains("replyToDeleted")
                    .contains("삭제된 댓글입니다.")
                    .doesNotContain("comment.updatedAt || comment.createdAt")
                    .doesNotContain("comment.updatedAt !== comment.createdAt");
            assertOrdered(script,
                    "actions.append(makeLikeControl(comment, loggedIn))",
                    "actions.append(makeButton('답글'",
                    "makeButton('수정'",
                    "makeButton('삭제'");
        }

        assertThat(resource("static/js/post-comments.js"))
                .contains("/post-comments")
                .contains("post-comment-like-button")
                .contains("post-comment-reply-button")
                .contains("post-comment-edit")
                .contains("post-comment-delete");
        assertThat(resource("static/js/course-comments.js"))
                .contains("/course-comments")
                .contains("course-comment-like-button")
                .contains("course-comment-reply-button")
                .contains("course-comment-edit")
                .contains("course-comment-delete");
    }

    @Test
    void destinationRendererKeepsFeaturesAndUsesUnifiedAccessibleLikeUi() throws IOException {
        String renderer = resource("static/js/comment/render.js");
        assertThat(renderer)
                .contains("comment.writer?.profileImage")
                .contains("comment.writer?.id")
                .contains("content-comment-card")
                .contains("content-comment-avatar")
                .contains("content-comment-image")
                .contains("content-comment-actions")
                .contains("content-comment-replies")
                .contains("<span class=\"mention content-comment-mention\">")
                .contains("/images/default.png")
                .contains("fallbackApplied")
                .contains("/uploads/icons/like.png")
                .contains("/uploads/icons/like2.png")
                .contains("aria-hidden=\"true\"")
                .contains("aria-pressed")
                .contains("date.getFullYear() % 100")
                .contains("updatedAt > createdAt")
                .contains("formatDate(comment.createdAt)")
                .doesNotContain("/profile/")
                .doesNotContain("comment.updatedAt !== comment.createdAt");
        assertOrdered(renderer,
                "${likeControl}",
                "class=\"reply-btn content-comment-action\"",
                "class=\"edit-btn content-comment-action\"",
                "class=\"delete-btn content-comment-action\"");

        assertThat(resource("static/js/comment/events.js"))
                .contains("toggleLikeApi(id)")
                .contains("onCommentsReload()")
                .contains("[data-comment-sort]")
                .contains("setupCommentPagingEvents(onLoadMore)");
        assertThat(resource("static/js/comment/api.js"))
                .contains("/comments/${commentId}/like-toggle")
                .contains("/comments/list/page")
                .contains("/comments/images");
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(RESOURCES.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private void assertOrdered(String value, String... fragments) {
        int previous = -1;
        for (String fragment : fragments) {
            int current = value.indexOf(fragment, previous + 1);
            assertThat(current).as("%s 뒤에 %s가 있어야 함", previous, fragment).isGreaterThan(previous);
            previous = current;
        }
    }
}
