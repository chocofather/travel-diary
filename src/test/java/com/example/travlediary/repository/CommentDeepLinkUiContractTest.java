package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommentDeepLinkUiContractTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void sharedDeepLinkHelperValidatesIdScrollsActualTargetAndHighlightsWithoutFlashing() throws IOException {
        String helper = resource("static/js/comment-deep-link.js");
        String css = resource("static/css/content-comment.css");

        assertThat(helper)
                .contains("new URLSearchParams(global.location.search).get('commentId')")
                .contains("/^\\d+$/")
                .contains("Number.isSafeInteger(commentId) && commentId > 0")
                .contains("prefers-reduced-motion: reduce")
                .contains("scrollIntoView({behavior: scrollBehavior(), block: 'center'})")
                .contains("target.classList.add(highlightClass)")
                .contains("highlightDurationMs = 2000")
                .doesNotContain("location.replace")
                .doesNotContain("history.");
        assertThat(css)
                .contains(".content-comment-item.is-deep-link-target > .content-comment-card")
                .contains("background: #ecf9f6")
                .contains("box-shadow:")
                .doesNotContain("@keyframes");
    }

    @Test
    void postAndCourseLoadOnlyTheResolvedPageAndFocusTheActualReplyElement() throws IOException {
        assertDetailScript(
                resource("static/js/post-comments.js"),
                "/post-comments/${encodeURIComponent(targetCommentId)}/location",
                "?postId=${encodeURIComponent(postId)}",
                "data-comment-id");
        assertDetailScript(
                resource("static/js/course-comments.js"),
                "/course-comments/${encodeURIComponent(targetCommentId)}/location",
                "?courseId=${encodeURIComponent(courseId)}",
                "data-comment-id");
    }

    @Test
    void destinationUsesItsExistingModuleRendererAndDataIdForTheResolvedPage() throws IOException {
        String api = resource("static/js/comment/api.js");
        String init = resource("static/js/comment/init.js");

        assertThat(api)
                .contains("export function fetchCommentLocation(destinationId, commentId)")
                .contains("/comments/${encodeURIComponent(commentId)}/location")
                .contains("?destinationId=${encodeURIComponent(destinationId)}");
        assertThat(init)
                .contains("fetchCommentLocation(destinationId, targetCommentId)")
                .contains("const locationGeneration = ++requestGeneration")
                .contains("if (locationGeneration !== requestGeneration) return")
                .contains("await loadCommentPage({reset: true, pageOverride: targetPage})")
                .contains("deepLink.focusTarget(commentListEl, 'data-id', targetCommentId)")
                .contains("void loadInitialComments()")
                .doesNotContain("for (let page")
                .doesNotContain("while (nextPage");
    }

    @Test
    void everyDetailTemplateLoadsTheHelperBeforeItsCommentClient() throws IOException {
        assertOrdered(resource("templates/post/detail.html"),
                "/js/comment-deep-link.js", "/js/post-comments.js");
        assertOrdered(resource("templates/course/detail.html"),
                "/js/comment-deep-link.js", "/js/course-comments.js");
        assertThat(resource("templates/destination/detail.html"))
                .contains("/js/comment-deep-link.js")
                .contains("/js/comment/init.js");
    }

    private void assertDetailScript(String script, String endpoint, String targetParameter,
                                    String dataAttribute) {
        assertThat(script)
                .contains(endpoint)
                .contains(targetParameter)
                .contains("const targetPage = Number(location.page) - 1")
                .contains("const locationGeneration = ++requestGeneration")
                .contains("if (locationGeneration !== requestGeneration) return")
                .contains("await loadCommentPage({reset: true, pageOverride: targetPage})")
                .contains("deepLink.focusTarget(list, '" + dataAttribute + "', targetCommentId)")
                .contains("nextPage = page + 1")
                .contains("void loadInitialComments()")
                .doesNotContain("for (let page")
                .doesNotContain("while (nextPage");
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(RESOURCES.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private void assertOrdered(String value, String first, String second) {
        assertThat(value.indexOf(first)).isGreaterThanOrEqualTo(0);
        assertThat(value.indexOf(second)).isGreaterThan(value.indexOf(first));
    }
}
