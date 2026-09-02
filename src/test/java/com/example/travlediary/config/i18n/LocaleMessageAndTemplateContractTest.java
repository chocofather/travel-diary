package com.example.travlediary.config.i18n;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class LocaleMessageAndTemplateContractTest {

    @Test
    void everyLocaleBundleDefinesTheSameKeysAsTheKoreanFallbackBundle() throws IOException {
        Properties fallback = properties("/messages.properties");

        for (String localeBundle : new String[]{
                "/messages_ko.properties",
                "/messages_en.properties",
                "/messages_ja.properties",
                "/messages_zh_CN.properties",
                "/messages_zh_TW.properties"
        }) {
            assertThat(properties(localeBundle).stringPropertyNames())
                    .as("message keys in %s", localeBundle)
                    .containsExactlyInAnyOrderElementsOf(fallback.stringPropertyNames());
        }
    }

    @Test
    void headerMessagesResolveForAllFiveLanguagesIncludingBothChineseBundles() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messages.setFallbackToSystemLocale(false);

        assertThat(messages.getMessage("nav.community", null, Locale.forLanguageTag("ko")))
                .isEqualTo("여행 커뮤니티");
        assertThat(messages.getMessage("nav.community", null, Locale.forLanguageTag("en")))
                .isEqualTo("Community");
        assertThat(messages.getMessage("nav.community", null, Locale.forLanguageTag("ja")))
                .isEqualTo("コミュニティ");
        assertThat(messages.getMessage("nav.community", null, Locale.forLanguageTag("zh-CN")))
                .isEqualTo("旅行社区");
        assertThat(messages.getMessage("nav.community", null, Locale.forLanguageTag("zh-TW")))
                .isEqualTo("旅遊社群");

        assertThat(messages.getMessage("nav.community.question", null, Locale.forLanguageTag("ko")))
                .isEqualTo("여행 질문");
        assertThat(messages.getMessage("nav.community.question", null, Locale.forLanguageTag("en")))
                .isEqualTo("Travel Q&A");
        assertThat(messages.getMessage("nav.community.question", null, Locale.forLanguageTag("ja")))
                .isEqualTo("旅の質問");
        assertThat(messages.getMessage("nav.community.question", null, Locale.forLanguageTag("zh-CN")))
                .isEqualTo("旅行问答");
        assertThat(messages.getMessage("nav.community.question", null, Locale.forLanguageTag("zh-TW")))
                .isEqualTo("旅遊問答");

        assertThat(messages.getMessage("footer.privacy", null, Locale.forLanguageTag("ko")))
                .isEqualTo("개인정보처리방침");
        assertThat(messages.getMessage("footer.privacy", null, Locale.forLanguageTag("en")))
                .isEqualTo("Privacy Policy");
        assertThat(messages.getMessage("footer.privacy", null, Locale.forLanguageTag("ja")))
                .isEqualTo("プライバシーポリシー");
        assertThat(messages.getMessage("footer.privacy", null, Locale.forLanguageTag("zh-CN")))
                .isEqualTo("隐私政策");
        assertThat(messages.getMessage("footer.privacy", null, Locale.forLanguageTag("zh-TW")))
                .isEqualTo("隱私權政策");

        assertThat(messages.getMessage("home.course.stopCount", new Object[]{5},
                        Locale.forLanguageTag("ko"))).isEqualTo("장소 5곳");
        assertThat(messages.getMessage("home.course.stopCount", new Object[]{5},
                        Locale.forLanguageTag("en"))).isEqualTo("5 places");
        assertThat(messages.getMessage("home.course.stopCount", new Object[]{5},
                        Locale.forLanguageTag("ja"))).isEqualTo("5か所");
        assertThat(messages.getMessage("home.course.stopCount", new Object[]{5},
                        Locale.forLanguageTag("zh-CN"))).isEqualTo("5个地点");
        assertThat(messages.getMessage("home.course.stopCount", new Object[]{5},
                        Locale.forLanguageTag("zh-TW"))).isEqualTo("5個地點");
    }

    @Test
    void publicLayoutAndHeaderExposeTheLocaleContractsWithoutChangingAdminLang() throws IOException {
        var publicLayout = Jsoup.parse(resource("/templates/layout/main.html"));
        var adminLayout = Jsoup.parse(resource("/templates/layout/admin.html"));
        var header = Jsoup.parse(resource("/templates/fragments/header.html"));

        assertThat(publicLayout.selectFirst("html").attr("th:lang"))
                .isEqualTo("${currentLanguageTag}");
        assertThat(adminLayout.selectFirst("html").attr("lang")).isEqualTo("ko");
        assertThat(adminLayout.selectFirst("html").hasAttr("th:lang")).isFalse();

        assertThat(header.getAllElements().stream()
                .filter(element -> element.hasAttr("th:text"))
                .map(element -> element.attr("th:text")))
                .contains("#{nav.domestic}", "#{nav.community}", "#{auth.login}");
        assertThat(header.select("form.locale-option-form[method=post] input[name=languageTag]"))
                .hasSize(1);
        assertThat(header.select("form.locale-option-form").attr("th:each"))
                .isEqualTo("language : ${supportedLanguages}");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Properties properties(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            properties.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
