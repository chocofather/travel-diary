package com.example.travlediary.security;

import java.time.Instant;

public record LoginFormState(String email, int failureCount, Instant blockedUntil) {

    public static final String SESSION_ATTRIBUTE =
            LoginFormState.class.getName() + ".SESSION";
    private static final int MAX_EMAIL_LENGTH = 128;

    public LoginFormState {
        email = safeEmail(email);
        failureCount = Math.max(0, failureCount);
    }

    public static LoginFormState from(String email, LoginThrottleStatus status) {
        return new LoginFormState(
                email,
                status.accountFailureCount(),
                status.blockedUntil());
    }

    private static String safeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.length() <= MAX_EMAIL_LENGTH
                ? email
                : email.substring(0, MAX_EMAIL_LENGTH);
    }
}
