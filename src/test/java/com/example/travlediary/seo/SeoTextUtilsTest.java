package com.example.travlediary.seo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeoTextUtilsTest {

    @Test
    void summaryRemovesHtmlAndNormalizesWhitespace() {
        String summary = SeoTextUtils.summary(
                "<p>제주도의 <strong>푸른 바다</strong></p><p>산책 코스</p>");

        assertThat(summary).isEqualTo("제주도의 푸른 바다 산책 코스");
    }

    @Test
    void summaryTruncatesLongTextWithoutSplittingUnicodeCharacters() {
        String summary = SeoTextUtils.summary("😀".repeat(170));

        assertThat(summary.codePointCount(0, summary.length())).isEqualTo(160);
        assertThat(summary).endsWith("…");
    }
}
