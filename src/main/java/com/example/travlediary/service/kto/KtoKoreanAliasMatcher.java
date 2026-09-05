package com.example.travlediary.service.kto;

import java.util.regex.Pattern;

/**
 * 외국어 제목 끝에 붙는 한글 별칭을 다룬다.
 *
 * <p>TourAPI 외국어 서비스는 "Gyeongbokgung Palace (경복궁)" 처럼 원래 이름을 괄호로 덧붙인다.
 * 일본어·중국어 자료는 같은 자리에 전각 괄호 "（）" 를 쓰는 경우가 많아 둘 다 받는다.
 * 괄호 없이 이어 붙인 한글까지는 다루지 않는다. 오매칭 위험이 커서 별도로 판단할 문제다.
 */
final class KtoKoreanAliasMatcher {

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
        int start = trailingParenthesisStart(normalizedTitle);
        if (start < 0) {
            return null;
        }
        String alias = normalizeName(
                normalizedTitle.substring(start + 1, normalizedTitle.length() - 1));
        return alias != null && HANGUL.matcher(alias).find() && !LATIN.matcher(alias).find()
                ? alias : null;
    }

    static String stripTrailingKoreanAlias(String title) {
        String normalizedTitle = normalizeName(title);
        if (normalizedTitle == null || extractKoreanAlias(normalizedTitle) == null) {
            return normalizedTitle;
        }
        return normalizeName(normalizedTitle.substring(0, trailingParenthesisStart(normalizedTitle)));
    }

    /**
     * 제목 끝 괄호가 열리는 자리. 짝이 맞지 않으면 -1 이다.
     *
     * <p>국문 이름 자체가 괄호를 품는 축제가 있어 한 겹 안쪽까지 함께 본다.
     * 예: {@code "100 Years Night Market (백년나이트 야시장 (메기의 귀환))"} 의 별칭은
     * {@code "백년나이트 야시장 (메기의 귀환)"} 이다.
     *
     * <p>바깥 괄호는 반각끼리·전각끼리 짝이 맞아야 한다. 섞인 짝은 별칭으로 보지 않는다.
     */
    private static int trailingParenthesisStart(String title) {
        int end = title.length();
        if (end == 0) {
            return -1;
        }
        char closing = title.charAt(end - 1);
        if (!isClosingParenthesis(closing)) {
            return -1;
        }
        char expectedOpening = closing == ')' ? '(' : '（';
        int depth = 0;
        for (int index = end - 1; index >= 0; index--) {
            char character = title.charAt(index);
            if (isClosingParenthesis(character)) {
                depth++;
            } else if (isOpeningParenthesis(character)) {
                depth--;
                if (depth == 0) {
                    return character == expectedOpening ? index : -1;
                }
            }
        }
        return -1;
    }

    private static boolean isOpeningParenthesis(char character) {
        return character == '(' || character == '（';
    }

    private static boolean isClosingParenthesis(char character) {
        return character == ')' || character == '）';
    }
}
