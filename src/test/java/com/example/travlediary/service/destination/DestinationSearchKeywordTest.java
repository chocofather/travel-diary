package com.example.travlediary.service.destination;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 여행지 목록 검색어 해석 계약.
 * 초성만 입력하면 초성검색, 그 외에는 기존 이름 부분검색이다.
 */
class DestinationSearchKeywordTest {

    @Test
    void ordinaryKeywordStaysAPartialNameSearch() {
        DestinationSearchKeyword keyword = DestinationSearchKeyword.of("경복");

        assertThat(keyword.isEmpty()).isFalse();
        assertThat(keyword.namePattern()).isEqualTo("경복");
        assertThat(keyword.chosungPattern()).isNull();
    }

    @Test
    void partialSyllableInTheMiddleStillMatchesByName() {
        assertThat(DestinationSearchKeyword.of("복궁").namePattern()).isEqualTo("복궁");
    }

    @Test
    void initialConsonantsOnlyBecomeAChosungSearch() {
        DestinationSearchKeyword keyword = DestinationSearchKeyword.of("ㄱㅂㄱ");

        assertThat(keyword.namePattern()).isNull();
        assertThat(keyword.chosungPattern()).isNotNull();
        assertThat(matches(keyword, "경복궁")).isTrue();
        assertThat(matches(keyword, "서울 경복궁 야간개장")).isTrue();
        assertThat(matches(keyword, "창덕궁")).isFalse();
        assertThat(matches(keyword, "해운대 해수욕장")).isFalse();
    }

    @Test
    void chosungSearchWorksAcrossWordsWithSpaces() {
        DestinationSearchKeyword keyword = DestinationSearchKeyword.of("ㅎㅇㄷ ㅎㅅㅇㅈ");

        assertThat(matches(keyword, "해운대 해수욕장")).isTrue();
        assertThat(matches(keyword, "경복궁")).isFalse();
    }

    @Test
    void anotherChosungExampleMatchesItsOwnNameOnly() {
        DestinationSearchKeyword keyword = DestinationSearchKeyword.of("ㅊㄷㄱ");

        assertThat(matches(keyword, "창덕궁")).isTrue();
        assertThat(matches(keyword, "경복궁")).isFalse();
    }

    @Test
    void surroundingSpacesAreTrimmedBeforeDeciding() {
        DestinationSearchKeyword keyword = DestinationSearchKeyword.of("  ㄱㅂㄱ  ");

        assertThat(keyword.namePattern()).isNull();
        assertThat(matches(keyword, "경복궁")).isTrue();
    }

    @Test
    void mixedOrNonInitialJamoFallsBackToTheNameSearch() {
        // 초성이 아닌 겹자음(ㄳ)이나 글자가 섞이면 일반 검색이다
        assertThat(DestinationSearchKeyword.of("ㄳ").chosungPattern()).isNull();
        assertThat(DestinationSearchKeyword.of("경ㄱ").chosungPattern()).isNull();
        assertThat(DestinationSearchKeyword.of("ㄱㅂㄱ tower").chosungPattern()).isNull();
        assertThat(DestinationSearchKeyword.of("경ㄱ").namePattern()).isEqualTo("경ㄱ");
    }

    @Test
    void blankKeywordsCarryNoCondition() {
        for (String blank : new String[]{null, "", "   "}) {
            DestinationSearchKeyword keyword = DestinationSearchKeyword.of(blank);
            assertThat(keyword.isEmpty()).as("입력 %s", blank).isTrue();
            assertThat(keyword.namePattern()).isNull();
            assertThat(keyword.chosungPattern()).isNull();
        }
    }

    @Test
    void chosungPatternOnlyContainsSafeGeneratedCharacters() {
        String pattern = DestinationSearchKeyword.of("ㅎㅇㄷ ㅎㅅㅇㅈ").chosungPattern();

        // 사용자 입력이 그대로 패턴에 들어가지 않는다 (자모/따옴표/와일드카드 없음)
        assertThat(pattern).matches("(?:\\[[가-힣]-[가-힣]]|\\\\s\\*)+");
        assertThat(pattern).doesNotContain("ㅎ").doesNotContain("'").doesNotContain("%");
    }

    private boolean matches(DestinationSearchKeyword keyword, String name) {
        return Pattern.compile(keyword.chosungPattern()).matcher(name).find();
    }
}
