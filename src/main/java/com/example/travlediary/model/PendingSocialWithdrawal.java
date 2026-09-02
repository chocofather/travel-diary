package com.example.travlediary.model;

import java.io.Serializable;
import java.time.Instant;

public record PendingSocialWithdrawal(
        String flowId,
        Long userId,
        SocialProvider provider,
        Instant createdAt,
        Instant expiresAt) implements Serializable {

    public static final String SESSION_ATTRIBUTE = "pendingSocialWithdrawal";

    public boolean isValidAt(Instant now) {
        return flowId != null && !flowId.isBlank()
                && userId != null
                && provider != null
                && createdAt != null
                && expiresAt != null
                && expiresAt.isAfter(now);
    }
}
