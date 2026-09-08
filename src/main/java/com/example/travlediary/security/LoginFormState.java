package com.example.travlediary.security;

import java.time.Instant;

public record LoginFormState(String username, int failureCount, Instant blockedUntil) {

    public static final String SESSION_ATTRIBUTE =
            LoginFormState.class.getName() + ".SESSION";
    private static final int MAX_USERNAME_LENGTH = 128;

    public LoginFormState {
        username = safeUsername(username);
        failureCount = Math.max(0, failureCount);
    }

    public static LoginFormState from(String username, LoginThrottleStatus status) {
        return new LoginFormState(
                username,
                status.accountFailureCount(),
                status.blockedUntil());
    }

    private static String safeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.length() <= MAX_USERNAME_LENGTH
                ? username
                : username.substring(0, MAX_USERNAME_LENGTH);
    }
}
