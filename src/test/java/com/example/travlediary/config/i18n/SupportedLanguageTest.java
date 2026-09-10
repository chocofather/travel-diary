package com.example.travlediary.config.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    /** 지역/스크립트가 붙은 값도 하나의 지원 언어로 모은다. */
    @ParameterizedTest
    @CsvSource({
            "ko, KOREAN",
            "ko-KR, KOREAN",
            "en, ENGLISH",
            "en-US, ENGLISH",
            "en-GB, ENGLISH",
            "ja, JAPANESE",
            "ja-JP, JAPANESE",
            "zh, CHINESE_SIMPLIFIED",
            "zh-CN, CHINESE_SIMPLIFIED",
            "zh-SG, CHINESE_SIMPLIFIED",
            "zh-Hans, CHINESE_SIMPLIFIED",
            "zh-Hans-CN, CHINESE_SIMPLIFIED",
            "zh-TW, CHINESE_TRADITIONAL",
            "zh-HK, CHINESE_TRADITIONAL",
            "zh-MO, CHINESE_TRADITIONAL",
            "zh-Hant, CHINESE_TRADITIONAL",
            "zh-Hant-HK, CHINESE_TRADITIONAL"
    })
    void normalizesRegionAndScriptVariantsOntoASupportedLanguage(
            String languageTag, SupportedLanguage expected) {
        assertThat(SupportedLanguage.normalize(languageTag)).contains(expected);
        assertThat(SupportedLanguage.normalize(Locale.forLanguageTag(languageTag)))
                .contains(expected);
    }

    /** 스크립트가 지역보다 우선한다. */
    @Test
    void chineseScriptSubtagWinsOverTheRegion() {
        assertThat(SupportedLanguage.normalize("zh-Hans-HK"))
                .contains(SupportedLanguage.CHINESE_SIMPLIFIED);
        assertThat(SupportedLanguage.normalize("zh-Hant-CN"))
                .contains(SupportedLanguage.CHINESE_TRADITIONAL);
    }

    @Test
    void normalizeRejectsUnsupportedLanguages() {
        assertThat(SupportedLanguage.normalize("fr-FR")).isEmpty();
        assertThat(SupportedLanguage.normalize("de")).isEmpty();
        assertThat(SupportedLanguage.normalize("es-ES")).isEmpty();
        assertThat(SupportedLanguage.normalize("abc")).isEmpty();
        assertThat(SupportedLanguage.normalize("*")).isEmpty();
        assertThat(SupportedLanguage.normalize((String) null)).isEmpty();
        assertThat(SupportedLanguage.normalize((Locale) null)).isEmpty();
    }

    /** Accept-Language 는 q 값 순서를 따르고 매칭이 없으면 English 로 떨어진다. */
    @Test
    void acceptLanguageFollowsQualityOrderAndFallsBackToEnglish() {
        assertThat(SupportedLanguage.fromAcceptLanguage("ko-KR,ko;q=0.9,en-US;q=0.8"))
                .isEqualTo(SupportedLanguage.KOREAN);
        assertThat(SupportedLanguage.fromAcceptLanguage("fr-FR,de;q=0.9,ja;q=0.8"))
                .isEqualTo(SupportedLanguage.JAPANESE);
        // q 값이 더 높은 지원 언어를 앞선 미지원 언어보다 먼저 고르지는 않는다(선호 순서 유지).
        assertThat(SupportedLanguage.fromAcceptLanguage("zh-HK,en;q=0.5"))
                .isEqualTo(SupportedLanguage.CHINESE_TRADITIONAL);
        assertThat(SupportedLanguage.fromAcceptLanguage("fr-FR,de;q=0.9"))
                .isEqualTo(SupportedLanguage.ENGLISH);
        assertThat(SupportedLanguage.fromAcceptLanguage("*")).isEqualTo(SupportedLanguage.ENGLISH);
        assertThat(SupportedLanguage.fromAcceptLanguage("not a header"))
                .isEqualTo(SupportedLanguage.ENGLISH);
        assertThat(SupportedLanguage.fromAcceptLanguage("")).isEqualTo(SupportedLanguage.ENGLISH);
        assertThat(SupportedLanguage.fromAcceptLanguage(null))
                .isEqualTo(SupportedLanguage.ENGLISH);
    }
}
