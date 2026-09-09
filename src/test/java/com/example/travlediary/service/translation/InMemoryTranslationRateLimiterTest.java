package com.example.travlediary.service.translation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTranslationRateLimiterTest {

    @Test
    void limitsRepeatedRequestsByIp() {
        TranslationProperties properties = new TranslationProperties(
                2000, Duration.ofSeconds(30), Duration.ofSeconds(30),
                1, 10, 10, Duration.ofMinutes(1), true, 400_000L,
                20_000L, 5_000L);
        InMemoryTranslationRateLimiter limiter = new InMemoryTranslationRateLimiter(
                properties, Clock.fixed(Instant.parse("2026-09-09T01:00:00Z"), ZoneOffset.UTC));

        limiter.checkRequest("203.0.113.4", 1L);

        assertThatThrownBy(() -> limiter.checkRequest("203.0.113.4", 2L))
                .isInstanceOf(TranslationRateLimitException.class);
    }

    @Test
    void limitsRepeatedRequestsByAuthenticatedUserAcrossIps() {
        TranslationProperties properties = new TranslationProperties(
                2000, Duration.ofSeconds(30), Duration.ofSeconds(30),
                10, 1, 10, Duration.ofMinutes(1), true, 400_000L,
                20_000L, 5_000L);
        InMemoryTranslationRateLimiter limiter = new InMemoryTranslationRateLimiter(
                properties, Clock.fixed(Instant.parse("2026-09-09T01:00:00Z"), ZoneOffset.UTC));

        limiter.checkRequest("203.0.113.4", 7L);

        assertThatThrownBy(() -> limiter.checkRequest("203.0.113.5", 7L))
                .isInstanceOf(TranslationRateLimitException.class);
    }
}
