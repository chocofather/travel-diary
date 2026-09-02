package com.example.travlediary.config.i18n;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum SupportedLanguage {
    KOREAN("ko", "한국어"),
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    CHINESE_TRADITIONAL("zh-TW", "繁體中文");

    private static final List<SupportedLanguage> ALL = List.of(values());

    private final String languageTag;
    private final Locale locale;
    private final String displayName;

    SupportedLanguage(String languageTag, String displayName) {
        this.languageTag = languageTag;
        this.locale = Locale.forLanguageTag(languageTag);
        this.displayName = displayName;
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static List<SupportedLanguage> all() {
        return ALL;
    }

    public static Optional<SupportedLanguage> fromLanguageTag(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return Optional.empty();
        }
        String canonicalTag = Locale.forLanguageTag(languageTag.strip()).toLanguageTag();
        return Arrays.stream(values())
                .filter(language -> language.languageTag.equals(canonicalTag))
                .findFirst();
    }

    public static Optional<SupportedLanguage> fromLocale(Locale locale) {
        return locale == null ? Optional.empty() : fromLanguageTag(locale.toLanguageTag());
    }
}
