package com.example.travlediary.service.user;

import java.util.regex.Pattern;

public final class FullNamePolicy {

    public static final String INPUT_PATTERN =
            "^ *[가-힣A-Za-z]+(?: +[가-힣A-Za-z]+)* *$";
    public static final String INVALID_MESSAGE =
            "이름은 한글, 영문과 이름 사이의 공백만 입력할 수 있습니다.";

    private static final int MAX_LENGTH = 50;
    private static final Pattern VALID_NAME = Pattern.compile(
            "^[가-힣A-Za-z]+(?: [가-힣A-Za-z]+)*$");

    private FullNamePolicy() {
    }

    public static String normalizeAndValidate(String fullName) {
        String normalized = fullName == null
                ? ""
                : fullName.strip().replaceAll(" +", " ");
        if (normalized.isEmpty()
                || normalized.length() > MAX_LENGTH
                || !VALID_NAME.matcher(normalized).matches()) {
            throw new RegistrationValidationException("fullName", INVALID_MESSAGE);
        }
        return normalized;
    }
}
