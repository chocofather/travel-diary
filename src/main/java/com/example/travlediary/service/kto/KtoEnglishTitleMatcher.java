package com.example.travlediary.service.kto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class KtoEnglishTitleMatcher {

    private static final Pattern TRAILING_PARENTHESIZED_TEXT = Pattern.compile("\\(([^()]*)\\)\\s*$");
    private static final Pattern HANGUL = Pattern.compile("[\\u1100-\\u11FF\\u3130-\\u318F\\uAC00-\\uD7AF]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

    private KtoEnglishTitleMatcher() {
    }

    static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().replaceAll("\\s+", " ");
    }

    static String extractKoreanAlias(String title) {
        String normalizedTitle = normalizeName(title);
        if (normalizedTitle == null) {
            return null;
        }
        Matcher matcher = TRAILING_PARENTHESIZED_TEXT.matcher(normalizedTitle);
        if (!matcher.find()) {
            return null;
        }
        String alias = normalizeName(matcher.group(1));
        return alias != null && HANGUL.matcher(alias).find() && !LATIN.matcher(alias).find()
                ? alias : null;
    }

    static String stripTrailingKoreanAlias(String title) {
        String normalizedTitle = normalizeName(title);
        if (normalizedTitle == null || extractKoreanAlias(normalizedTitle) == null) {
            return normalizedTitle;
        }
        Matcher matcher = TRAILING_PARENTHESIZED_TEXT.matcher(normalizedTitle);
        if (!matcher.find()) {
            return normalizedTitle;
        }
        return normalizeName(normalizedTitle.substring(0, matcher.start()));
    }
}
