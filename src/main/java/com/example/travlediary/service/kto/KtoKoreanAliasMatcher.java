package com.example.travlediary.service.kto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 외국어 제목 끝에 붙는 한글 별칭을 다룬다.
 *
 * <p>TourAPI 외국어 서비스는 "Gyeongbokgung Palace (경복궁)" 처럼 원래 이름을 괄호로 덧붙인다.
 * 일본어·중국어 자료는 같은 자리에 전각 괄호 "（）" 를 쓰는 경우가 많아 둘 다 받는다.
 * 괄호 없이 이어 붙인 한글까지는 다루지 않는다. 오매칭 위험이 커서 별도로 판단할 문제다.
 */
final class KtoKoreanAliasMatcher {

    /** 반각 "(...)" 과 전각 "（...）" 를 같은 자리로 본다. 짝이 맞아야 한다. */
    private static final Pattern TRAILING_PARENTHESIZED_TEXT =
            Pattern.compile("(?:\\(([^()（）]*)\\)|（([^()（）]*)）)\\s*$");
    private static final Pattern HANGUL = Pattern.compile("[\\u1100-\\u11FF\\u3130-\\u318F\\uAC00-\\uD7AF]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

    private KtoKoreanAliasMatcher() {
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
        String alias = normalizeName(parenthesizedText(matcher));
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

    /** 반각/전각 중 실제로 잡힌 쪽의 안쪽 글자. */
    private static String parenthesizedText(Matcher matcher) {
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }
}
