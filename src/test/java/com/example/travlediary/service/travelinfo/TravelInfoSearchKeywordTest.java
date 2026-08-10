package com.example.travlediary.service.travelinfo;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TravelInfoSearchKeywordTest {

    @Test
    void normalizesNullBlankWhitespaceOneCharacterAndMaximumLength() {
        assertThat(TravelInfoSearchKeyword.normalize(null)).isNull();
        assertThat(TravelInfoSearchKeyword.normalize("   \t\n")).isNull();
        assertThat(TravelInfoSearchKeyword.normalize("  파  ")).isEqualTo("파");
        assertThat(TravelInfoSearchKeyword.normalize("  파리  여행  "))
                .isEqualTo("파리  여행");
        assertThat(TravelInfoSearchKeyword.normalize("가".repeat(101)))
                .isEqualTo("가".repeat(100));
        assertThat(TravelInfoSearchKeyword.normalize("😀".repeat(101)))
                .isEqualTo("😀".repeat(100));
    }

    @Test
    void escapesEveryLikeWildcardAsALiteralUsingExclamationMark() {
        assertThat(TravelInfoSearchKeyword.toLikeLiteral("%"))
                .isEqualTo("!%");
        assertThat(TravelInfoSearchKeyword.toLikeLiteral("_"))
                .isEqualTo("!_");
        assertThat(TravelInfoSearchKeyword.toLikeLiteral("!"))
                .isEqualTo("!!");
        assertThat(TravelInfoSearchKeyword.toLikeLiteral("100%"))
                .isEqualTo("100!%");
        assertThat(TravelInfoSearchKeyword.toLikeLiteral("a_b"))
                .isEqualTo("a!_b");
        assertThat(TravelInfoSearchKeyword.toLikeLiteral("!!test"))
                .isEqualTo("!!!!test");
        assertThat(TravelInfoSearchKeyword.toLikeLiteral(" 100%_!!test "))
                .isEqualTo("100!%!_!!!!test");
    }

    @Test
    void buildsBoundRegexForKoreanInitialConsonantsAndImeIntermediateValues() {
        assertThat(matches("썸네일", "ㅆ")).isTrue();
        assertThat(matches("썸네일", "써")).isTrue();
        assertThat(matches("썸네일", "썸")).isTrue();
        assertThat(matches("썸네일", "썸ㄴ")).isTrue();
        assertThat(matches("썸네일", "썸네")).isTrue();
        assertThat(matches("썸네일", "썸네ㅇ")).isTrue();
        assertThat(matches("썸네일", "썸네일")).isTrue();
        assertThat(matches("서울 여행", "ㅅ")).isTrue();
        assertThat(matches("서울 여행", "ㅅㅇ")).isTrue();
        assertThat(matches("벚꽃 명소", "ㅂㄲ")).isTrue();
        assertThat(matches("파리 여행", "파")).isTrue();
    }

    @Test
    void escapesRegexMetacharactersAndSkipsRegexWhenLikeIsSufficient() {
        assertThat(TravelInfoSearchKeyword.toKoreanPrefixRegex("썸"))
                .isNull();
        assertThat(TravelInfoSearchKeyword.toKoreanPrefixRegex("ㅅ."))
                .endsWith("\\.");
        assertThat(matches("서울 여행", "ㅅ."))
                .isFalse();
    }

    private boolean matches(String title, String keyword) {
        String normalized = TravelInfoSearchKeyword.normalize(keyword);
        String koreanPattern = TravelInfoSearchKeyword.toKoreanPrefixRegex(keyword);
        return title.contains(normalized)
                || koreanPattern != null && Pattern.compile(koreanPattern).matcher(title).find();
    }
}
