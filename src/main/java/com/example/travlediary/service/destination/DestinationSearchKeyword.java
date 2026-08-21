package com.example.travlediary.service.destination;

/**
 * 관리자 여행지 목록의 검색어 해석.
 * 초성(ㄱ~ㅎ)과 공백만 입력하면 초성검색, 그 외에는 기존 이름 부분검색이다.
 *
 * <p>초성검색은 schema 변경 없이, 각 초성이 담당하는 완성형 한글 범위를 이어붙인
 * 정규식 패턴으로 바꿔 Mapper 에 bind parameter 로 넘긴다. 패턴은 아래 상수들로만
 * 조립되므로 사용자 입력이 그대로 SQL/정규식에 들어가지 않는다.</p>
 */
public final class DestinationSearchKeyword {

    /** 완성형 한글 초성 19자 (유니코드 초성 순서) */
    private static final String INITIAL_CONSONANTS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final char HANGUL_BASE = '가';
    /** 초성 하나가 담당하는 완성형 글자 수 (중성 21 × 종성 28) */
    private static final int SYLLABLES_PER_INITIAL = 21 * 28;
    private static final String WHITESPACE_PATTERN = "\\s*";

    private static final DestinationSearchKeyword EMPTY = new DestinationSearchKeyword(null, null);

    private final String namePattern;
    private final String chosungPattern;

    private DestinationSearchKeyword(String namePattern, String chosungPattern) {
        this.namePattern = namePattern;
        this.chosungPattern = chosungPattern;
    }

    public static DestinationSearchKeyword of(String rawKeyword) {
        if (rawKeyword == null || rawKeyword.isBlank()) {
            return EMPTY;
        }

        String keyword = rawKeyword.strip();
        String chosungPattern = chosungPattern(keyword);
        return chosungPattern == null
                ? new DestinationSearchKeyword(keyword, null)
                : new DestinationSearchKeyword(null, chosungPattern);
    }

    /** 일반 부분검색어. 초성검색이거나 검색어가 없으면 null. */
    public String namePattern() {
        return namePattern;
    }

    /** 초성검색 정규식. 일반 검색이거나 검색어가 없으면 null. */
    public String chosungPattern() {
        return chosungPattern;
    }

    public boolean isEmpty() {
        return namePattern == null && chosungPattern == null;
    }

    /** 초성(과 공백)으로만 이루어진 검색어면 정규식을, 아니면 null 을 돌려준다. */
    private static String chosungPattern(String keyword) {
        StringBuilder pattern = new StringBuilder();
        boolean hasInitial = false;

        for (int index = 0; index < keyword.length(); index++) {
            char character = keyword.charAt(index);
            if (Character.isWhitespace(character)) {
                pattern.append(WHITESPACE_PATTERN);
                continue;
            }

            int initialIndex = INITIAL_CONSONANTS.indexOf(character);
            if (initialIndex < 0) {
                return null;
            }
            char first = (char) (HANGUL_BASE + initialIndex * SYLLABLES_PER_INITIAL);
            char last = (char) (first + SYLLABLES_PER_INITIAL - 1);
            pattern.append('[').append(first).append('-').append(last).append(']');
            hasInitial = true;
        }

        return hasInitial ? pattern.toString() : null;
    }
}
