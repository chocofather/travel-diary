package com.example.travlediary.service.translation;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryTranslationRateLimiter implements TranslationRateLimiter {
    private static final long CLEANUP_INTERVAL = 256L;
    private static final int MAX_TRACKED_KEYS_PER_BUCKET = 10_000;

    private final TranslationProperties properties;
    private final Clock clock;
    private final Map<String, WindowCounter> requestCounters = new ConcurrentHashMap<>();
    private final Map<String, WindowCounter> externalCounters = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();

    @Autowired
    public InMemoryTranslationRateLimiter(TranslationProperties properties) {
        this(properties, Clock.systemUTC());
    }

    InMemoryTranslationRateLimiter(TranslationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void checkRequest(String ipAddress, Long userId) {
        Instant now = clock.instant();
        acquire(requestCounters, "ip:" + normalizeIp(ipAddress), properties.ipRequests(), now);
        if (userId != null) {
            acquire(requestCounters, "user:" + userId, properties.userRequests(), now);
        }
        cleanupIfNeeded(now);
    }

    @Override
    public void checkExternalCall(String ipAddress, Long userId) {
        Instant now = clock.instant();
        acquire(externalCounters, "ip:" + normalizeIp(ipAddress), properties.externalRequests(), now);
        if (userId != null) {
            acquire(externalCounters, "user:" + userId, properties.externalRequests(), now);
        }
        cleanupIfNeeded(now);
    }

    private void acquire(Map<String, WindowCounter> counters, String key, int limit, Instant now) {
        if (limit <= 0) throw new TranslationRateLimitException(properties.rateLimitWindow().toSeconds());
        if (!counters.containsKey(key) && counters.size() >= MAX_TRACKED_KEYS_PER_BUCKET) {
            removeExpired(counters, now);
            if (counters.size() >= MAX_TRACKED_KEYS_PER_BUCKET) {
                throw new TranslationRateLimitException(properties.rateLimitWindow().toSeconds());
            }
        }
        WindowCounter counter = counters.computeIfAbsent(
                key, ignored -> new WindowCounter(now.plus(properties.rateLimitWindow())));
        long retryAfter = counter.acquire(now, limit, properties.rateLimitWindow());
        if (retryAfter > 0) throw new TranslationRateLimitException(retryAfter);
    }

    private void cleanupIfNeeded(Instant now) {
        if (operations.incrementAndGet() % CLEANUP_INTERVAL != 0) return;
        removeExpired(requestCounters, now);
        removeExpired(externalCounters, now);
    }

    private void removeExpired(Map<String, WindowCounter> counters, Instant now) {
        counters.entrySet().removeIf(entry -> entry.getValue().expiredBefore(now));
    }

    private String normalizeIp(String ipAddress) {
        return ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress;
    }

    private static final class WindowCounter {
        private Instant windowEndsAt;
        private int count;

        private WindowCounter(Instant windowEndsAt) {
            this.windowEndsAt = windowEndsAt;
        }

        synchronized long acquire(Instant now, int limit, Duration window) {
            if (!now.isBefore(windowEndsAt)) {
                windowEndsAt = now.plus(window);
                count = 0;
            }
            if (count >= limit) {
                long millis = Duration.between(now, windowEndsAt).toMillis();
                return Math.max(1L, (millis + 999L) / 1000L);
            }
            count++;
            return 0L;
        }

        synchronized boolean expiredBefore(Instant now) {
            return !now.isBefore(windowEndsAt);
        }
    }
}
