package com.example.travlediary.service.travelinfo;

public final class TravelInfoSearchKeyword {

    public static final int MAX_LENGTH = 100;

    private static final int HANGUL_SYLLABLE_BASE = 0xAC00;
    private static final int HANGUL_SYLLABLE_END = 0xD7A3;
    private static final int HANGUL_FINAL_COUNT = 28;
    private static final int HANGUL_INITIAL_BLOCK_SIZE = 21 * HANGUL_FINAL_COUNT;
    private static final int MODERN_INITIAL_JAMO_BASE = 0x1100;
    private static final int MODERN_INITIAL_JAMO_END = 0x1112;
    private static final String COMPATIBILITY_INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final String REGEX_META_CHARACTERS = "\\.^$|?*+()[]{}";

    private TravelInfoSearchKeyword() {
    }

    public static String normalize(String keyword) {
        if (keyword == null) {
            return null;
        }

        String normalized = keyword.strip();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.codePointCount(0, normalized.length()) <= MAX_LENGTH) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, MAX_LENGTH);
        return normalized.substring(0, endIndex);
    }

    public static String toLikeLiteral(String keyword) {
        String normalized = normalize(keyword);
        if (normalized == null) {
            return null;
        }
        return normalized
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /**
     * 제목 LIKE 검색으로 잡히지 않는 한글 초성 및 IME 미완성 음절을 위한 정규식이다.
     * 초성은 해당 초성의 모든 완성형 음절 범위로, 검색어 끝의 받침 없는 음절은
     * 같은 초성/중성에 받침이 붙을 수 있는 범위로 확장한다.
     */
    public static String toKoreanPrefixRegex(String keyword) {
        String normalized = normalize(keyword);
        if (normalized == null) {
            return null;
        }

        int[] codePoints = normalized.codePoints().toArray();
        StringBuilder pattern = new StringBuilder(normalized.length() * 2);
        boolean expanded = false;

        for (int index = 0; index < codePoints.length; index++) {
            int codePoint = codePoints[index];
            int initialIndex = getInitialIndex(codePoint);

            if (initialIndex >= 0) {
                int rangeStart = HANGUL_SYLLABLE_BASE + initialIndex * HANGUL_INITIAL_BLOCK_SIZE;
                appendRange(pattern, rangeStart, rangeStart + HANGUL_INITIAL_BLOCK_SIZE - 1);
                expanded = true;
                continue;
            }

            boolean isLastCharacter = index == codePoints.length - 1;
            if (isLastCharacter && isOpenHangulSyllable(codePoint)) {
                appendRange(pattern, codePoint, codePoint + HANGUL_FINAL_COUNT - 1);
                expanded = true;
                continue;
            }

            appendRegexLiteral(pattern, codePoint);
        }

        return expanded ? pattern.toString() : null;
    }

    private static int getInitialIndex(int codePoint) {
        int compatibilityIndex = COMPATIBILITY_INITIALS.indexOf(codePoint);
        if (compatibilityIndex >= 0) {
            return compatibilityIndex;
        }
        if (codePoint >= MODERN_INITIAL_JAMO_BASE && codePoint <= MODERN_INITIAL_JAMO_END) {
            return codePoint - MODERN_INITIAL_JAMO_BASE;
        }
        return -1;
    }

    private static boolean isOpenHangulSyllable(int codePoint) {
        return codePoint >= HANGUL_SYLLABLE_BASE
                && codePoint <= HANGUL_SYLLABLE_END
                && (codePoint - HANGUL_SYLLABLE_BASE) % HANGUL_FINAL_COUNT == 0;
    }

    private static void appendRange(StringBuilder pattern, int start, int end) {
        pattern.append('[')
                .appendCodePoint(start)
                .append('-')
                .appendCodePoint(end)
                .append(']');
    }

    private static void appendRegexLiteral(StringBuilder pattern, int codePoint) {
        if (REGEX_META_CHARACTERS.indexOf(codePoint) >= 0) {
            pattern.append('\\');
        }
        pattern.appendCodePoint(codePoint);
    }
}
