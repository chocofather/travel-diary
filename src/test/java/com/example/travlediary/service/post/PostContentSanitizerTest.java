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
