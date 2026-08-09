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
    void keepsOnlyConfiguredKoreanQuillFontClasses() {
        String html = "<span class=\"ql-font-pretendard\">프리텐다드</span>"
                + "<span class=\"ql-font-noto-sans-kr\">노토 산스</span>"
                + "<span class=\"ql-font-noto-serif-kr\">노토 세리프</span>"
                + "<span class=\"ql-font-nanum-human\">나눔휴먼</span>"
                + "<span class=\"ql-font-school-safe-bareonbatang\">학교안심 바른바탕</span>"
                + "<span class=\"ql-font-cafe24-dongdong\">카페24 동동</span>"
                + "<span class=\"ql-font-gangwon-saeeum\">강원교육새음</span>"
                + "<span class=\"ql-font-unknown\">미등록</span>";

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned)
                .contains("class=\"ql-font-pretendard\"")
                .contains("class=\"ql-font-noto-sans-kr\"")
                .contains("class=\"ql-font-noto-serif-kr\"")
                .contains("class=\"ql-font-nanum-human\"")
                .contains("class=\"ql-font-school-safe-bareonbatang\"")
                .contains("class=\"ql-font-cafe24-dongdong\"")
                .contains("class=\"ql-font-gangwon-saeeum\"")
                .doesNotContain("ql-font-unknown");
    }

    @Test
    void keepsOnlyQuillChecklistStateAttributeValues() {
        String html = "<ul><li data-list=\"unchecked\">미완료</li>"
                + "<li data-list=\"checked\">완료</li>"
                + "<li data-list=\"pending\" data-arbitrary=\"value\">비정상</li></ul>";

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned)
                .contains("<li data-list=\"unchecked\">미완료</li>")
                .contains("<li data-list=\"checked\">완료</li>")
                .contains("<li>비정상</li>")
                .doesNotContain("pending", "data-arbitrary");
    }

    @Test
    void keepsOnlyQuillIndentClassesFromOneThroughEight() {
        String html = "<p class=\"ql-indent-1\">한 단계</p>"
                + "<p class=\"ql-indent-4 ql-align-center\">네 단계</p>"
                + "<p class=\"ql-indent-8\">여덟 단계</p>"
                + "<p class=\"ql-indent-0\">영 단계</p>"
                + "<p class=\"ql-indent-9 ql-indent-999\">과도한 단계</p>";

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned)
                .contains("class=\"ql-indent-1\"")
                .contains("class=\"ql-indent-4 ql-align-center\"")
                .contains("class=\"ql-indent-8\"")
                .doesNotContain("ql-indent-0", "ql-indent-9", "ql-indent-999");
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
    void keepsExistingImagesAndValidatedNumericWidths() {
        String html = "<img src=\"/uploads/editor/original.png\" alt=\"원본\">"
                + "<img src=\"/uploads/editor/first.png\" width=\"320\" alt=\"첫번째\">"
                + "<img src=\"/uploads/editor/second.png\" width=\"900\" alt=\"두번째\">"
                + "<img src=\"/uploads/editor/infographic.png\" width=\"1200\" alt=\"인포그래픽\">";

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned)
                .contains("src=\"/uploads/editor/original.png\" alt=\"원본\"")
                .contains("src=\"/uploads/editor/first.png\" width=\"320\"")
                .contains("src=\"/uploads/editor/second.png\" width=\"900\"")
                .contains("src=\"/uploads/editor/infographic.png\" width=\"1200\"");
    }

    @Test
    void removesOutOfRangeOrMalformedImageWidthsAndImageStyles() {
        String html = "<img src=\"/uploads/editor/a.png\" width=\"119\">"
                + "<img src=\"/uploads/editor/b.png\" width=\"1201\">"
                + "<img src=\"/uploads/editor/c.png\" width=\"-300\">"
                + "<img src=\"/uploads/editor/d.png\" width=\"600px\">"
                + "<img src=\"/uploads/editor/e.png\" width=\"100%\">"
                + "<img src=\"/uploads/editor/f.png\" width=\"999999\">"
                + "<img src=\"/uploads/editor/g.png\" style=\"width: 600px; height: auto\">";

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned)
                .doesNotContain("width=", "style=");
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
