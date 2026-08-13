package com.example.travlediary.service.user;

import java.util.regex.Pattern;

public final class PasswordPolicy {

    public static final String INVALID_MESSAGE =
            "비밀번호는 8자 이상이며, 영문, 숫자, !@#$%^&*만 사용하고 특수문자를 1개 이상 포함해야 합니다.";
    public static final String MISMATCH_MESSAGE = "새 비밀번호가 일치하지 않습니다.";

    private static final Pattern ALLOWED_PATTERN = Pattern.compile(
            "^(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]{8,}$");

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || !ALLOWED_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(INVALID_MESSAGE);
        }
    }

    public static boolean isValid(String password) {
        return password != null && ALLOWED_PATTERN.matcher(password).matches();
    }

    public static void validateConfirmation(String password, String passwordConfirmation) {
        if (password == null || !password.equals(passwordConfirmation)) {
            throw new IllegalArgumentException(MISMATCH_MESSAGE);
        }
    }
}
