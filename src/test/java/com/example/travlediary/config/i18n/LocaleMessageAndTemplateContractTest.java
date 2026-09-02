package com.example.travlediary.config.i18n;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LocaleMessageAndTemplateContractTest {

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
                .isEqualTo("旅行コミュニティ");
        assertThat(messages.getMessage("nav.community", null, Locale.forLanguageTag("zh-CN")))
                .isEqualTo("旅行社区");
        assertThat(messages.getMessage("nav.community", null, Locale.forLanguageTag("zh-TW")))
                .isEqualTo("旅遊社群");
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
}
