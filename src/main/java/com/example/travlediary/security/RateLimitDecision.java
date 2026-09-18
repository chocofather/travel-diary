package com.example.travlediary.security;

import java.time.Duration;

/**
 * 요청 하나에 대한 제한 판정.
 *
 * @param allowed        지나가도 되는지
 * @param retryAfter     막혔을 때 다시 시도할 수 있을 때까지 남은 시간 (허용이면 null)
 * @param firstRejection 이 창에서 처음 막힌 요청인지. 로그를 한 창에 한 줄만 남기는 데 쓴다.
 */
public record RateLimitDecision(boolean allowed, Duration retryAfter, boolean firstRejection) {

    public static RateLimitDecision pass() {
        return new RateLimitDecision(true, null, false);
    }

    public static RateLimitDecision reject(Duration retryAfter, boolean firstRejection) {
        return new RateLimitDecision(false, retryAfter, firstRejection);
    }

    /** 응답 {@code Retry-After} 에 실을 초. 0 을 내보내지 않도록 최소 1초로 올린다. */
    public long retryAfterSeconds() {
        if (retryAfter == null) {
            return 0;
        }
        long seconds = (retryAfter.toMillis() + 999L) / 1000L;
        return Math.max(1L, seconds);
    }
}
