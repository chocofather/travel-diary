package com.example.travlediary.model;

import java.io.Serializable;
import java.time.Instant;

public record PendingSocialSignup(
        String flowId,
        SocialProvider provider,
        String providerUserId,
        String providerEmail,
        Boolean providerEmailVerified,
        Instant createdAt,
        Instant expiresAt
) implements Serializable {

    public static final String SESSION_ATTRIBUTE = "pendingSocialSignup";

    public boolean isExpired(Instant now) {
        return expiresAt == null || !now.isBefore(expiresAt);
    }
}
