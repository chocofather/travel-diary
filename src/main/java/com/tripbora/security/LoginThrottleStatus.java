package com.tripbora.security;

import java.time.Instant;

public record LoginThrottleStatus(int accountFailureCount, Instant blockedUntil) {

    public boolean blocked() {
        return blockedUntil != null;
    }
}
