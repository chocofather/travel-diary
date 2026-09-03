package com.example.travlediary.service.category;

import com.example.travlediary.config.i18n.SupportedLanguage;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class LocalizedReferenceNameResolver {

    private static final String KOREAN_LANGUAGE_TAG = SupportedLanguage.KOREAN.getLanguageTag();
    private static final String EMPTY_DISPLAY_NAME = "-";

    public String resolve(SupportedLanguage requestedLanguage,
                          String baseName,
                          List<LocalizedName> translations) {
        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        List<LocalizedName> available = translations == null
                ? List.of()
                : translations.stream()
                .filter(translation -> translation != null && hasText(translation.name()))
                .sorted(Comparator
                        .comparing(LocalizedName::languageCode,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(LocalizedName::id,
                                Comparator.nullsLast(Long::compareTo)))
                .toList();

        String requested = find(available, language.getLanguageTag());
        if (requested != null) {
            return requested;
        }
        String korean = find(available, KOREAN_LANGUAGE_TAG);
        if (korean != null) {
            return korean;
        }
        if (!available.isEmpty()) {
            return available.get(0).name().strip();
        }
        return hasText(baseName) ? baseName.strip() : EMPTY_DISPLAY_NAME;
    }

    private String find(List<LocalizedName> translations, String languageCode) {
        return translations.stream()
                .filter(translation -> languageCode.equals(translation.languageCode()))
                .map(LocalizedName::name)
                .findFirst()
                .map(String::strip)
                .orElse(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record LocalizedName(Long id, String languageCode, String name) {
    }
}
