package com.example.travlediary.model;

import java.io.Serializable;
import java.time.Instant;

public record PendingSocialConnection(
        String flowId,
        Long userId,
        SocialProvider provider,
        Instant createdAt,
        Instant expiresAt,
        String oauthState
) implements Serializable {

    public static final String SESSION_ATTRIBUTE = "pendingSocialConnection";

    public boolean isValidAt(Instant now) {
        return flowId != null && !flowId.isBlank()
                && userId != null
                && provider != null
                && createdAt != null
                && !createdAt.isAfter(now)
                && expiresAt != null
                && now.isBefore(expiresAt);
    }

    public PendingSocialConnection bindOAuthState(String state) {
        return new PendingSocialConnection(
                flowId, userId, provider, createdAt, expiresAt, state);
    }

    public boolean matchesOAuthState(String state) {
        return oauthState != null && !oauthState.isBlank() && oauthState.equals(state);
    }
}
