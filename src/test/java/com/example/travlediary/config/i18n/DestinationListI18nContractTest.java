package com.example.travlediary.config.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationListI18nContractTest {

    private static final String[] BUNDLES = {
            "/messages.properties",
            "/messages_ko.properties",
            "/messages_en.properties",
            "/messages_ja.properties",
            "/messages_zh_CN.properties",
            "/messages_zh_TW.properties"
    };

    @Test
    void regionSelectorsAndCardsRenderLocalizedDisplayValuesWithoutChangingIds() throws IOException {
        String source = resource("/templates/destination/fragment.html");
        assertThat(source)
                .contains("${regionDisplayNames[c.id]}")
                .contains("${regionDisplayNames[r.id]}")
                .contains("data-region-id=${c.id}")
                .contains("data-city-id=${r.id}")
                .doesNotContain("th:text=\"${c.regionName}\"")
                .doesNotContain("th:text=\"${r.regionName}\"");
        assertThat(source)
                .contains("th:text=\"${d.name}\"")
                .contains("th:text=\"${d.shortDescription}\"")
                .contains("th:text=\"${d.regionName}\"")
                .contains("#{destination.list.card.region}");
    }

    @Test
    void titlesSortControlsAndAccessibleLabelsUseDestinationListMessages() throws IOException {
        String source = resource("/templates/destination/fragment.html");

        assertThat(source)
                .contains("#{destination.list.title.region(${selectedCityName})}")
                .contains("#{destination.list.title.domestic}")
                .contains("#{destination.list.title.international}")
                .contains("#{destination.list.sort.label}")
                .contains("#{destination.list.sort.default}")
                .contains("#{destination.list.sort.views}")
                .contains("#{destination.list.sort.bookmarks}")
                .contains("#{destination.list.region.previous}")
                .contains("#{destination.list.region.next}")
                .contains("#{destination.list.pagination.previous}")
                .contains("#{destination.list.pagination.next}");
    }

    @Test
    void destinationListMessagesResolveForAllSupportedLocales() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messages.setFallbackToSystemLocale(false);

        assertThat(message(messages, "destination.list.title.domestic", "ko"))
                .isEqualTo("국내 여행지");
        assertThat(message(messages, "destination.list.title.domestic", "en"))
                .isEqualTo("Domestic Destinations");
        assertThat(message(messages, "destination.list.title.region", "ja", "ソウル"))
                .isEqualTo("ソウルの旅行スポット");
        assertThat(message(messages, "destination.list.card.region", "zh-CN"))
                .isEqualTo("地区:");
        assertThat(message(messages, "destination.list.card.region", "zh-TW"))
                .isEqualTo("地區：");
    }

    @Test
    void everyBundleContainsEveryDestinationListKey() throws IOException {
        Properties fallback = properties(BUNDLES[0]);
        var listKeys = fallback.stringPropertyNames().stream()
                .filter(key -> key.startsWith("destination.list."))
                .toList();

        assertThat(listKeys).isNotEmpty();
        for (String bundle : BUNDLES) {
            assertThat(properties(bundle).stringPropertyNames())
                    .as(bundle)
                    .containsAll(listKeys);
        }
    }

    private String message(ResourceBundleMessageSource messages, String key, String languageTag,
                           Object... arguments) {
        return messages.getMessage(key, arguments, Locale.forLanguageTag(languageTag));
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
