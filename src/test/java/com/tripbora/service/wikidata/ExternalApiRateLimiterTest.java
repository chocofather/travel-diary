package com.tripbora.service.wikidata;

import com.tripbora.service.wikidata.ExternalApiRateLimiter.Limited;
import com.tripbora.service.wikidata.ExternalApiRateLimiter.Service;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalApiRateLimiterTest {
    private final WikidataBulkRegistrationServiceTest.MutableClock clock = new WikidataBulkRegistrationServiceTest.MutableClock();
    private final List<Duration> slept = new ArrayList<>();
    /** 대기하면 그만큼 시각이 흐른다. */
    private final ExternalApiRateLimiter limiter = new ExternalApiRateLimiter(clock, duration -> {
        slept.add(duration);
        clock.advance(duration);
    }, () -> 0.5, ExternalApiRateLimiter.MAX_ATTEMPTS);

    @Test
    void retryAfterIsFollowedFirstAndTheRequestSucceedsAfterWaiting() {
        AtomicInteger calls = new AtomicInteger();

        String result = limiter.call(Service.WIKIDATA, () -> {
            if (calls.incrementAndGet() == 1) throw new Limited(Duration.ofSeconds(3));
            return "ok";
        }, this::exhausted);

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
        assertThat(slept).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void withoutRetryAfterTheWaitGrowsExponentiallyWithJitterAndIsCapped() {
        ExternalApiRateLimiter low = new ExternalApiRateLimiter(clock, duration -> { }, () -> 0.0, 3);
        ExternalApiRateLimiter high = new ExternalApiRateLimiter(clock, duration -> { }, () -> 1.0, 3);

        // 2초 → 4초 → 8초 … 에 ±20% 흔들기, 60초 상한
        assertThat(low.recordLimited(Service.WIKIPEDIA, null)).isEqualTo(Duration.ofMillis(1600));
        assertThat(high.recordLimited(Service.WIKIPEDIA, null)).isEqualTo(Duration.ofMillis(2400));
        assertThat(high.recordLimited(Service.WIKIPEDIA, null)).isEqualTo(Duration.ofMillis(4800));
        for (int index = 0; index < 8; index++) high.recordLimited(Service.WIKIPEDIA, null);
        assertThat(high.recordLimited(Service.WIKIPEDIA, null)).isEqualTo(ExternalApiRateLimiter.MAX_BACKOFF);
        // 비정상적으로 긴 Retry-After 는 상한까지만 막는다.
        assertThat(low.recordLimited(Service.COMMONS, Duration.ofHours(5))).isEqualTo(ExternalApiRateLimiter.MAX_RETRY_AFTER);
    }

    @Test
    void retriesAreBoundedAndLongWaitsAreHandedBackInsteadOfBlockingTheRequest() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> limiter.call(Service.COMMONS, () -> {
            calls.incrementAndGet();
            throw new Limited(Duration.ofSeconds(2));
        }, this::exhausted)).isInstanceOf(CommonsRateLimitException.class);
        assertThat(calls).hasValue(ExternalApiRateLimiter.MAX_ATTEMPTS);

        calls.set(0);
        assertThatThrownBy(() -> limiter.call(Service.WIKIDATA, () -> {
            calls.incrementAndGet();
            throw new Limited(Duration.ofSeconds(90));
        }, this::exhausted))
                .isInstanceOfSatisfying(CommonsRateLimitException.class,
                        limited -> assertThat(limited.retryAfter()).isEqualTo(Duration.ofSeconds(90)));
        // 긴 대기는 요청 안에서 기다리지 않고 바로 돌려준다(한 번만 불렀다).
        assertThat(calls).hasValue(1);
    }

    @Test
    void waitIsSharedPerServiceAndDoesNotBlockOtherServices() {
        limiter.recordLimited(Service.WIKIDATA, Duration.ofSeconds(5));
        AtomicInteger commonsCalls = new AtomicInteger();

        limiter.call(Service.COMMONS, commonsCalls::incrementAndGet, this::exhausted);
        assertThat(slept).isEmpty();

        // 다른 요청이 받은 Wikidata 대기를 먼저 지킨 뒤 부른다.
        limiter.call(Service.WIKIDATA, () -> "ok", this::exhausted);
        assertThat(slept).containsExactly(Duration.ofSeconds(5));
        assertThat(commonsCalls).hasValue(1);
    }

    @Test
    void retryAfterHeaderAcceptsSecondsAndHttpDates() {
        Clock fixed = Clock.fixed(Instant.parse("2026-09-26T00:00:00Z"), ZoneOffset.UTC);

        assertThat(ExternalApiRateLimiter.parseRetryAfter("7", fixed)).isEqualTo(Duration.ofSeconds(7));
        assertThat(ExternalApiRateLimiter.parseRetryAfter("Sat, 26 Sep 2026 00:00:30 GMT", fixed))
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(ExternalApiRateLimiter.parseRetryAfter("soon", fixed)).isNull();
        assertThat(ExternalApiRateLimiter.parseRetryAfter(null, fixed)).isNull();
    }

    @Test
    void rateLimitsAreFoundInsideWrappedCauses() {
        var wrapped = new IllegalStateException("prepare failed", new WikipediaRateLimitException(Duration.ofSeconds(4)));

        assertThat(ExternalApiRateLimiter.rateLimitOf(wrapped)).get()
                .extracting(ExternalApiRateLimiter.RateLimited::service).isEqualTo(Service.WIKIPEDIA);
        assertThat(ExternalApiRateLimiter.rateLimitOf(new WikidataApiException("403"))).isEmpty();
    }

    private RuntimeException exhausted(Duration wait) {
        return new CommonsRateLimitException(wait);
    }
}
