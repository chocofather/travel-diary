package com.example.travlediary.service.user;

import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailPolicy {

    public static final String INVALID_MESSAGE = "올바른 이메일 주소를 입력해주세요.";

    private static final int MAX_LENGTH = 100;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9-]+(?:\\.[A-Z0-9-]+)+$",
            Pattern.CASE_INSENSITIVE);

    private EmailPolicy() {
    }

    public static String normalizeAndValidate(String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_LENGTH || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new RegistrationValidationException("userEmail", INVALID_MESSAGE);
        }
        return normalized;
    }

    public static String mask(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "";
        }
        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        int visibleLength = Math.min(3, Math.max(1, localPart.length() / 2));
        return localPart.substring(0, visibleLength) + "***" + domain;
    }
}
