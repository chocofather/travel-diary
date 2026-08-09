package com.example.travlediary.service.post;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostContentSanitizerTest {

    private final PostContentSanitizer sanitizer = new PostContentSanitizer();

    @Test
    void nullContentBecomesEmptyString() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
    }

    @Test
    void keepsToastEditorFormattingAndSafeImageSources() {
        String html = "<h2>제목</h2><p><strong>본문</strong></p>"
                + "<table><tbody><tr><td>표</td></tr></tbody></table>"
                + "<img src=\"/uploads/editor/a.png\" alt=\"내부\">"
                + "<img src=\"images/b.png\" alt=\"상대\">"
                + "<img src=\"https://example.com/c.png\" alt=\"외부\">";

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned)
                .contains("<h2>제목</h2>")
                .contains("<strong>본문</strong>")
                .contains("<table>")
                .contains("src=\"/uploads/editor/a.png\"")
                .contains("src=\"images/b.png\"")
                .contains("src=\"https://example.com/c.png\"");
    }

    @Test
    void keepsAllowedQuillFontSizeAndAlignmentClasses() {
        String html = "<p class=\"ql-align-center\">"
                + "<strong class=\"ql-font-serif ql-size-large\">본문</strong>"
                + "</p><blockquote class=\"ql-align-right\">인용</blockquote>";

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned)
                .contains("class=\"ql-align-center\"")
                .contains("class=\"ql-font-serif ql-size-large\"")
                .contains("class=\"ql-align-right\"");
    }

    @Test
    void keepsAllowedQuillColorAndBackgroundStyles() {
        String html = "<span style=\"color: #e60000; background-color: rgb(255, 255, 0)\">색상</span>";

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned)
                .contains("color: #e60000")
                .contains("background-color: rgb(255, 255, 0)");
    }

    @Test
    void keepsQuillStrikeTag() {
        assertThat(sanitizer.sanitize("<p><s>취소선</s></p>"))
                .contains("<s>취소선</s>");
    }

    @Test
    void keepsSafeTelephoneLinks() {
        String cleaned = sanitizer.sanitize("<a href=\"tel:01012345678\">전화</a>"
                + "<a href=\"tel:javascript:alert(1)\">위험</a>");

        assertThat(cleaned)
                .contains("href=\"tel:01012345678\"")
                .doesNotContain("tel:javascript:");
    }

    @Test
    void removesUnknownClassesWhileKeepingAllowedQuillClasses() {
        String html = "<span class=\"evil ql-font-monospace ql-size-giant\">본문</span>"
                + "<p class=\"ql-align-justify arbitrary\">문단</p>"
                + "<img class=\"arbitrary\" src=\"/uploads/editor/a.png\">";

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned)
                .contains("class=\"ql-font-monospace\"")
                .contains("class=\"ql-align-justify\"")
                .doesNotContain("evil", "ql-size-giant", "arbitrary");
    }

    @Test
    void removesDangerousStylesAndInvalidColorValues() {
        String html = "<span style=\"color: url(javascript:alert(1)); "
                + "background-color: #fff; background-image: url(https://example.com/a.png); "
                + "width: expression(alert(1))\">본문</span>";

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned)
                .contains("background-color: #fff")
                .doesNotContain("javascript:", "background-image", "url(", "expression", "width");
    }

    @Test
    void removesExecutableMarkupAndUnsafeImageSources() {
        String html = "<script>alert(1)</script>"
                + "<iframe src=\"https://example.com\"></iframe>"
                + "<p onclick=\"alert(1)\">본문</p>"
                + "<img src=\"javascript:alert(1)\" onerror=\"alert(1)\">"
                + "<img src=\"data:image/png;base64,abc\">"
                + "<a href=\"javascript:alert(1)\">링크</a>";

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned)
                .doesNotContain("<script")
                .doesNotContain("<iframe")
                .doesNotContain("onclick")
                .doesNotContain("onerror")
                .doesNotContain("javascript:")
                .doesNotContain("data:image");
    }
}
