package com.example.travlediary.service.user;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknamePolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "여행민준", "minjun", "MINJUN", "민준2026", "Travel2026", "여행왕123",
            "정상닉네임", "manager", "class", "쓰레기통", "A1", "가나다라마바사아자차카타",
            "여행2026", "민준123", "서울2025여행", "제주123가자"
    })
    void allowsValidNicknamesThatDoNotMatchForbiddenRules(String nickname) {
        assertThat(NicknamePolicy.normalizeAndValidate(nickname)).isEqualTo(nickname);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  여행민준  ", "\tminjun\n"})
    void stripsLeadingAndTrailingWhitespaceBeforeValidation(String nickname) {
        assertThat(NicknamePolicy.normalizeAndValidate(nickname))
                .isEqualTo(nickname.strip());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ", "한", "가나다라마바사아자차카타파", "여행 민준", "민준!", "min_jun", "min-jun"
    })
    void rejectsInvalidFormatWithTheSharedMessage(String nickname) {
        assertThatThrownBy(() -> NicknamePolicy.normalizeAndValidate(nickname))
                .isInstanceOfSatisfying(NicknamePolicy.ViolationException.class, exception -> {
                    assertThat(exception.getViolationType())
                            .isEqualTo(NicknamePolicy.ViolationType.INVALID_FORMAT);
                    assertThat(exception.getMessage()).isEqualTo(NicknamePolicy.INVALID_MESSAGE);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "관리자", "관리자123", "진짜관리자", "운영자아님", "운영진123", "어드민",
            "admin", "Admin", "ADMIN", "admin123", "traveladmin",
            "staff123", "official123", "여행일기123", "TravelDiary", "TravleDiary"
    })
    void blocksImpersonationRulesAsContainsIgnoringEnglishCase(String nickname) {
        assertForbidden(nickname);
    }

    @ParameterizedTest
    @ValueSource(strings = {"씨발123", "개새끼", "좆123", "fuckyou", "병신", "병신123", "등신", "멍청이123"})
    void blocksClearProfanityAndAbusiveContainsRules(String nickname) {
        assertForbidden(nickname);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "씨1발", "병1신", "병12신", "병123신",
            "관1리자", "관리123자", "관12리34자",
            "운1영자", "운12영34진",
            "a1d2m3i4n", "ad123min"
    })
    void blocksForbiddenRulesDisguisedByInsertedDigits(String nickname) {
        assertForbidden(nickname);
    }

    @ParameterizedTest
    @ValueSource(strings = {"adm1n", "0fficial", "st4ff"})
    void blocksConservativeLeetspeakForEnglishRules(String nickname) {
        assertForbidden(nickname);
    }

    @ParameterizedTest
    @ValueSource(strings = {"시발", "shit", "ass", "쓰레기"})
    void blocksExactRulesWhenTheWholeNicknameMatches(String nickname) {
        assertForbidden(nickname);
    }

    @ParameterizedTest
    @ValueSource(strings = {"시발점", "shitake", "class", "쓰레기통"})
    void exactRulesDoNotBlockAnOtherwiseValidLongerNickname(String nickname) {
        assertThat(NicknamePolicy.normalizeAndValidate(nickname)).isEqualTo(nickname);
    }

    @ParameterizedTest
    @ValueSource(strings = {"시1발", "a1s1s"})
    void exactRulesBlockWhenTheWholeDigitRemovedVariantMatches(String nickname) {
        assertForbidden(nickname);
    }

    @ParameterizedTest
    @ValueSource(strings = {"시1발점", "cl4ss", "쓰1레기통"})
    void exactRulesRemainExactForCanonicalVariants(String nickname) {
        assertThat(NicknamePolicy.normalizeAndValidate(nickname)).isEqualTo(nickname);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ＡＤＭＩＮ", "Ａｄｍｉｎ", "ｏｆｆｉｃｉａｌ"})
    void forbiddenInspectionUsesNfkcNormalization(String nickname) {
        assertThat(NicknamePolicy.isForbidden(nickname)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"administrator", "ADMINISTRATOR"})
    void configuredRuleCanBeInspectedEvenWhenLengthValidationWouldFailFirst(String nickname) {
        assertThat(NicknamePolicy.isForbidden(nickname)).isTrue();
    }

    private void assertForbidden(String nickname) {
        assertThatThrownBy(() -> NicknamePolicy.normalizeAndValidate(nickname))
                .isInstanceOfSatisfying(NicknamePolicy.ViolationException.class, exception -> {
                    assertThat(exception.getViolationType())
                            .isEqualTo(NicknamePolicy.ViolationType.FORBIDDEN);
                    assertThat(exception.getMessage()).isEqualTo(NicknamePolicy.FORBIDDEN_MESSAGE);
                });
    }
}
