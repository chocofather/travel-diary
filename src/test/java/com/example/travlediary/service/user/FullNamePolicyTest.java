package com.example.travlediary.service.user;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FullNamePolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"김민준", "홍 길동", "John Doe"})
    void acceptsKoreanEnglishAndInternalSpaces(String fullName) {
        assertThat(FullNamePolicy.normalizeAndValidate(fullName)).isEqualTo(fullName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"김민준1", "John!", "김@민준", "김🙂민준"})
    void rejectsDigitsSpecialCharactersAndEmoji(String fullName) {
        assertThatThrownBy(() -> FullNamePolicy.normalizeAndValidate(fullName))
                .isInstanceOf(RegistrationValidationException.class)
                .hasMessage(FullNamePolicy.INVALID_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  홍   길동  ", "  John   Doe  "})
    void trimsAndCollapsesSpaces(String fullName) {
        assertThat(FullNamePolicy.normalizeAndValidate(fullName))
                .doesNotStartWith(" ")
                .doesNotEndWith(" ")
                .doesNotContain("  ");
    }
}
