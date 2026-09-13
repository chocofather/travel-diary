package com.example.travlediary.service.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgeVerificationPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

    /** 14번째 생일 당일은 가입할 수 있다. */
    @Test
    void theFourteenthBirthdayItselfPasses() {
        LocalDate fourteenthBirthday = TODAY.minusYears(14);

        assertThat(AgeVerificationPolicy.isOldEnough(fourteenthBirthday, TODAY)).isTrue();
        assertThatCode(() -> AgeVerificationPolicy.verify(fourteenthBirthday.toString(), TODAY))
                .doesNotThrowAnyException();
    }

    /** 내일이 14번째 생일이면 아직 만 14세가 아니다. */
    @Test
    void theDayBeforeTheFourteenthBirthdayIsBlocked() {
        LocalDate oneDayShort = TODAY.minusYears(14).plusDays(1);

        assertThat(AgeVerificationPolicy.isOldEnough(oneDayShort, TODAY)).isFalse();
        assertThatThrownBy(() -> AgeVerificationPolicy.verify(oneDayShort.toString(), TODAY))
                .isInstanceOfSatisfying(RegistrationValidationException.class, exception -> {
                    assertThat(exception.getField()).isEqualTo("birthDate");
                    assertThat(exception.getMessageCode())
                            .isEqualTo("signup.error.birthDate.underage");
                });
    }

    /** 2월 29일생은 그 해에 같은 날짜가 없어도 경계가 밀리지 않는다. */
    @Test
    void aLeapDayBirthdayIsHandledOnTheNonLeapYearBoundary() {
        LocalDate leapDay = LocalDate.of(2012, 2, 29);

        // 2026-02-28 이 만 14세가 되는 날이다.
        assertThat(AgeVerificationPolicy.isOldEnough(leapDay, LocalDate.of(2026, 2, 28))).isTrue();
        assertThat(AgeVerificationPolicy.isOldEnough(leapDay, LocalDate.of(2026, 2, 27))).isFalse();
    }

    @Test
    void aFutureDateIsRejected() {
        assertThatThrownBy(() -> AgeVerificationPolicy.verify(TODAY.plusDays(1).toString(), TODAY))
                .isInstanceOfSatisfying(RegistrationValidationException.class, exception ->
                        assertThat(exception.getMessageCode())
                                .isEqualTo("signup.error.birthDate.invalid"));
        // 오늘 태어난 아기는 미래는 아니지만 만 14세도 아니다.
        assertThatThrownBy(() -> AgeVerificationPolicy.verify(TODAY.toString(), TODAY))
                .isInstanceOfSatisfying(RegistrationValidationException.class, exception ->
                        assertThat(exception.getMessageCode())
                                .isEqualTo("signup.error.birthDate.underage"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "2000-13-01", "2025-02-30", "2000/01/01",
            "20000101", "not-a-date", "1899-12-31"})
    void malformedImpossibleOrAbsurdDatesAreRejected(String rawBirthDate) {
        assertThatThrownBy(() -> AgeVerificationPolicy.verify(rawBirthDate, TODAY))
                .isInstanceOfSatisfying(RegistrationValidationException.class, exception ->
                        assertThat(exception.getMessageCode())
                                .isEqualTo("signup.error.birthDate.invalid"));
    }

    @Test
    void surroundingWhitespaceIsAccepted() {
        assertThatCode(() -> AgeVerificationPolicy.verify("  2000-01-01  ", TODAY))
                .doesNotThrowAnyException();
    }
}
