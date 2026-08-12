package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MyPageCommentUiContractTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void commentsPageUsesTheExistingLayoutAndSafeServerRenderedLinks() throws IOException {
        String template = resource("templates/mypage/comments.html");

        assertThat(template)
                .contains("layout/main :: layout")
                .contains("fragments/mypage/navigation :: navigation('comments')")
                .contains("/css/mypage-layout.css")
                .contains("/css/mypage-comments.css")
                .contains("th:text=\"${comment.contentPreview}\"")
                .contains("th:text=\"${comment.targetTitle}\"")
                .contains("@{/mypage/comments(type=${type},page=${currentPage + 1})}")
                .doesNotContain("th:utext")
                .doesNotContain("<script");
        assertThat(occurrences(template, "@{/destinations/{id}")).isEqualTo(2);
        assertThat(occurrences(template, "@{/post/{id}")).isEqualTo(2);
        assertThat(occurrences(template, "@{/course/{id}")).isEqualTo(2);
        assertThat(occurrences(template, "commentId=${comment.commentId}")).isEqualTo(6);
        assertThat(template)
                .doesNotContain("target=\"_blank\"")
                .doesNotContain("#comment-");
    }

    @Test
    void commentsStylesClampPreviewAndStackOnTheExistingMobileBreakpoint() throws IOException {
        String css = resource("static/css/mypage-comments.css");

        assertThat(css)
                .contains(".mypage-comment-preview")
                .contains(".mypage-comment-target a:focus-visible")
                .contains(".mypage-comment-target a:hover")
                .contains("-webkit-line-clamp: 3")
                .contains("overflow-wrap: anywhere")
                .contains("@media (max-width: 680px)")
                .contains("flex-direction: column")
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
                .doesNotContain("overflow-x: auto");
    }

    @Test
    void navigationAndMainActivateOnlyTheNewCommentsEntry() throws IOException {
        String navigation = resource("templates/fragments/mypage/navigation.html");
        String index = resource("templates/mypage/index.html");

        assertThat(navigation)
                .contains("activeMenu == 'comments'")
                .contains("th:href=\"@{/mypage/comments}\">내가 작성한 댓글</a>")
                .contains("activeMenu == 'bookmarks'")
                .contains("th:href=\"@{/mypage/bookmarks}\">북마크</a>")
                .contains("is-disabled\" aria-disabled=\"true\">회원정보 수정");
        assertThat(index)
                .contains("<a class=\"mypage-menu-item\" th:href=\"@{/mypage/comments}\">")
                .contains("<strong>북마크</strong>")
                .contains("<strong>회원정보 수정</strong>");
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(RESOURCES.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private int occurrences(String value, String fragment) {
        return (value.length() - value.replace(fragment, "").length()) / fragment.length();
    }
}
