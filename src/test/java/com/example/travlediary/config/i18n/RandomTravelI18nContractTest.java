package com.example.travlediary.config.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class RandomTravelI18nContractTest {

    private static final String[] BUNDLES = {
            "/messages.properties",
            "/messages_ko.properties",
            "/messages_en.properties",
            "/messages_ja.properties",
            "/messages_zh_CN.properties",
            "/messages_zh_TW.properties"
    };

    @Test
    void randomTravelJavascriptConsumesRenderedMessagesInsteadOfKoreanUiLiterals()
            throws IOException {
        String template = resource("/templates/random-travel.html");
        String script = resource("/static/js/random-travel.js");

        assertThat(template)
                .contains("id=\"random-travel-i18n\"")
                .contains("#{random.travel.title}")
                .contains("#{random.travel.card.details}")
                .contains("#{random.travel.error.title}");
        assertThat(script)
                .contains("random-travel-i18n", ".dataset")
                .contains("randomI18n.cardDetails", "randomI18n.errorTitle")
                .doesNotContain(
                        "'여행 지역을 고르는 중...'",
                        "'다시 뽑기 ↻'",
                        "'자세히 보기'",
                        "'조건에 맞는 여행지를 찾지 못했어요.'",
                        "'여행지를 불러오지 못했어요.'");
    }

    @Test
    void randomTravelMessagesResolveForEverySupportedLocale() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messages.setFallbackToSystemLocale(false);

        assertThat(message(messages, "random.travel.draw.button", "ko"))
                .isEqualTo("여행지 뽑기");
        assertThat(message(messages, "random.travel.card.details", "en"))
                .isEqualTo("View details");
        assertThat(message(messages, "random.travel.card.details", "ja"))
                .isEqualTo("詳細を見る");
        assertThat(message(messages, "random.travel.scope.domestic", "zh-CN"))
                .isEqualTo("国内");
        assertThat(message(messages, "random.travel.scope.domestic", "zh-TW"))
                .isEqualTo("國內");
    }

    @Test
    void everyBundleContainsEveryRandomTravelKey() throws IOException {
        Properties fallback = properties(BUNDLES[0]);
        var randomTravelKeys = fallback.stringPropertyNames().stream()
                .filter(key -> key.startsWith("random.travel."))
                .toList();

        assertThat(randomTravelKeys).isNotEmpty();
        for (String bundle : BUNDLES) {
            assertThat(properties(bundle).stringPropertyNames())
                    .as(bundle)
                    .containsAll(randomTravelKeys);
        }
    }

    private String message(
            ResourceBundleMessageSource messages, String key, String languageTag) {
        return messages.getMessage(key, null, Locale.forLanguageTag(languageTag));
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Properties properties(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            properties.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
