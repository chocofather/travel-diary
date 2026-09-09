package com.example.travlediary.service.translation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationVisibilityTest {

    @Test
    void offersOnlyKnownSupportedLanguagesDifferentFromTheSiteLocale() {
        assertThat(TranslationVisibility.shouldOffer("ko", "ko")).isFalse();
        assertThat(TranslationVisibility.shouldOffer("en", "ko")).isTrue();
        assertThat(TranslationVisibility.shouldOffer("ja", "ko")).isTrue();
        assertThat(TranslationVisibility.shouldOffer("zh-CN", "zh-TW")).isTrue();
        assertThat(TranslationVisibility.shouldOffer("und", "ko")).isFalse();
        assertThat(TranslationVisibility.shouldOffer("fr", "ko")).isFalse();
        assertThat(TranslationVisibility.shouldOffer(null, "ko")).isFalse();
    }
}
