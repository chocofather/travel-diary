package com.example.travlediary.service.user;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 가입 연령 확인. Travel Diary 는 만 14세 이상만 가입할 수 있다.
 *
 * <p>생년월일은 판정에만 쓰고 어디에도 저장하지 않는다. 요청 처리 중에만 존재했다가
 * 통과 여부만 남기고 사라진다. users.user_birth 는 이 기능에서 쓰지 않는다.
 *
 * <p>클라이언트 값은 믿지 않는다. 문자열 파싱과 경계 판정을 모두 서버에서 다시 한다.
 */
public final class AgeVerificationPolicy {

    public static final int MINIMUM_AGE = 14;

    public static final String INVALID_MESSAGE = "생년월일을 정확히 입력해주세요.";
    public static final String UNDERAGE_MESSAGE = "Travel Diary는 만 14세 이상부터 가입할 수 있습니다.";

    public static final String FIELD = "birthDate";
    public static final String INVALID_CODE = "signup.error.birthDate.invalid";
    public static final String UNDERAGE_CODE = "signup.error.birthDate.underage";

    /** 사람이 입력할 수 있는 가장 이른 생년월일. 오타로 들어온 연도를 걸러내기 위한 하한이다. */
    private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private AgeVerificationPolicy() {
    }

    /**
     * 입력값을 검증한다. 통과하면 아무것도 돌려주지 않는다.
     * 생년월일 자체를 밖으로 내보내지 않아 호출한 쪽이 실수로 저장할 여지를 남기지 않는다.
     *
     * @throws RegistrationValidationException 형식이 틀렸거나 만 14세 미만인 경우
     */
    public static void verify(String rawBirthDate, LocalDate today) {
        LocalDate birthDate = parse(rawBirthDate, today);
        if (!isOldEnough(birthDate, today)) {
            throw new RegistrationValidationException(FIELD, UNDERAGE_MESSAGE, UNDERAGE_CODE);
        }
    }

    /**
     * 만 나이 경계. 14번째 생일 당일은 통과하고 그 전날까지는 통과하지 못한다.
     * 2월 29일생처럼 그 해에 같은 날짜가 없으면 {@code plusYears} 가 2월 28일로 맞춰 준다.
     */
    public static boolean isOldEnough(LocalDate birthDate, LocalDate today) {
        return birthDate != null && today != null
                && !birthDate.plusYears(MINIMUM_AGE).isAfter(today);
    }

    private static LocalDate parse(String rawBirthDate, LocalDate today) {
        if (rawBirthDate == null || rawBirthDate.isBlank() || today == null) {
            throw new RegistrationValidationException(FIELD, INVALID_MESSAGE, INVALID_CODE);
        }
        final LocalDate birthDate;
        try {
            // 존재하지 않는 날짜(2025-02-30 등)는 여기서 그대로 걸린다.
            birthDate = LocalDate.parse(rawBirthDate.strip());
        } catch (DateTimeParseException exception) {
            throw new RegistrationValidationException(FIELD, INVALID_MESSAGE, INVALID_CODE);
        }
        if (birthDate.isAfter(today) || birthDate.isBefore(EARLIEST_BIRTH_DATE)) {
            throw new RegistrationValidationException(FIELD, INVALID_MESSAGE, INVALID_CODE);
        }
        return birthDate;
    }
}
