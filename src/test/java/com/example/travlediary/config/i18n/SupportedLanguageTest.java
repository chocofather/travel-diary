package com.example.travlediary.config.i18n;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedLanguageTest {

    @Test
    void exposesExactlyTheFiveCanonicalSupportedLanguages() {
        assertThat(SupportedLanguage.values())
                .extracting(SupportedLanguage::getLanguageTag)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(List.of(SupportedLanguage.values()))
                .extracting(SupportedLanguage::getDisplayName)
                .containsExactly("한국어", "English", "日本語", "简体中文", "繁體中文");
    }

    @Test
    void parsesSupportedBcp47TagsThroughJavaLocale() {
        assertThat(SupportedLanguage.fromLanguageTag("en"))
                .contains(SupportedLanguage.ENGLISH);
        assertThat(SupportedLanguage.fromLanguageTag("ja"))
                .contains(SupportedLanguage.JAPANESE);
        assertThat(SupportedLanguage.fromLanguageTag("zh-CN"))
                .contains(SupportedLanguage.CHINESE_SIMPLIFIED);
        assertThat(SupportedLanguage.fromLanguageTag("zh-TW"))
                .contains(SupportedLanguage.CHINESE_TRADITIONAL);
        assertThat(SupportedLanguage.CHINESE_SIMPLIFIED.getLocale())
                .isEqualTo(Locale.forLanguageTag("zh-CN"));
    }

    @Test
    void doesNotActivateUnsupportedOrPartialLanguageTags() {
        assertThat(SupportedLanguage.fromLanguageTag("fr")).isEmpty();
        assertThat(SupportedLanguage.fromLanguageTag("abc")).isEmpty();
        assertThat(SupportedLanguage.fromLanguageTag("zh")).isEmpty();
        assertThat(SupportedLanguage.fromLanguageTag("zh_CN")).isEmpty();
        assertThat(SupportedLanguage.fromLanguageTag(null)).isEmpty();
    }
}
