package com.example.travlediary.config.i18n;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum SupportedLanguage {
    KOREAN("ko", "한국어"),
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    CHINESE_TRADITIONAL("zh-TW", "繁體中文");

    private static final List<SupportedLanguage> ALL = List.of(values());
    /** 번체를 쓰는 지역. 스크립트 subtag 가 없는 zh-HK / zh-MO 같은 값을 가른다. */
    private static final Set<String> TRADITIONAL_CHINESE_REGIONS = Set.of("TW", "HK", "MO");
    private static final String TRADITIONAL_CHINESE_SCRIPT = "Hant";
    private static final String SIMPLIFIED_CHINESE_SCRIPT = "Hans";

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

    /**
     * 임의의 Locale 을 사이트 지원 언어로 정규화한다.
     * 지역/스크립트가 달라도 같은 언어로 묶고(ko-KR → ko, en-GB → en, zh-HK → zh-TW),
     * 지원하지 않는 언어면 비어 있는 값을 준다. 언어 코드 정규화는 이 메서드 한 곳에서만 한다.
     */
    public static Optional<SupportedLanguage> normalize(Locale locale) {
        if (locale == null) {
            return Optional.empty();
        }
        return switch (locale.getLanguage()) {
            case "ko" -> Optional.of(KOREAN);
            case "en" -> Optional.of(ENGLISH);
            case "ja" -> Optional.of(JAPANESE);
            case "zh" -> Optional.of(isTraditionalChinese(locale)
                    ? CHINESE_TRADITIONAL
                    : CHINESE_SIMPLIFIED);
            default -> Optional.empty();
        };
    }

    /** 문자열 language tag 를 지원 언어로 정규화한다. 해석할 수 없으면 비어 있는 값을 준다. */
    public static Optional<SupportedLanguage> normalize(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return Optional.empty();
        }
        return normalize(Locale.forLanguageTag(languageTag.strip()));
    }

    /**
     * 브라우저 Accept-Language 헤더를 지원 언어로 해석한다.
     * q 값 정렬과 헤더 파싱은 Java 의 LanguageRange 파서를 그대로 쓰고,
     * 선호 순서대로 처음 매칭되는 언어를 고른다. 매칭이 없으면 English 로 떨어진다.
     */
    public static SupportedLanguage fromAcceptLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return ENGLISH;
        }
        List<Locale.LanguageRange> ranges;
        try {
            ranges = Locale.LanguageRange.parse(acceptLanguage);
        } catch (IllegalArgumentException malformedHeader) {
            return ENGLISH;
        }
        return ranges.stream()
                .map(range -> Locale.forLanguageTag(range.getRange()))
                .map(SupportedLanguage::normalize)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(ENGLISH);
    }

    /** 중국어는 스크립트를 먼저 보고, 없으면 지역으로 번체/간체를 가른다. */
    private static boolean isTraditionalChinese(Locale locale) {
        String script = locale.getScript();
        if (TRADITIONAL_CHINESE_SCRIPT.equalsIgnoreCase(script)) {
            return true;
        }
        if (SIMPLIFIED_CHINESE_SCRIPT.equalsIgnoreCase(script)) {
            return false;
        }
        return TRADITIONAL_CHINESE_REGIONS.contains(
                locale.getCountry().toUpperCase(Locale.ROOT));
    }
}
