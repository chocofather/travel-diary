package com.tripbora.service.wikidata;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Wikimedia 외부 API의 요청 제한(HTTP 429 등) 대기 상태. 서비스마다 따로, 서버 전체에서 공유한다.
 *
 * <p>한 요청이 제한을 받으면 그 서비스의 대기 시각을 정하고, 같은 서비스를 부르는 다른 요청도 그 시각까지 먼저 기다린다.
 * Wikidata가 막혔다고 Commons·Wikipedia까지 기다리지는 않는다.</p>
 *
 * <p>대기 시간: 응답의 Retry-After(초 또는 HTTP 날짜)를 우선 따르고({@link #MAX_RETRY_AFTER}까지), 없으면
 * {@link #BASE_BACKOFF}부터 두 배씩 늘리되 {@link #MAX_BACKOFF}를 넘지 않고 ±20% 흔든다(jitter).
 * 한 요청 안에서는 최대 {@value #MAX_ATTEMPTS}번, 누적 {@link #MAX_INLINE_WAIT}까지만 기다리고,
 * 그보다 길면 기다리지 않고 {@link RateLimited} 예외로 돌려준다. 더 긴 대기는 화면(해외 일괄 등록)이 이어서 맡는다.</p>
 *
 * <p>403 같은 접근 거부나 형식 오류는 여기로 오지 않는다. 호출하는 쪽이 {@link Limited}를 던진 경우만 재시도한다.</p>
 */
@Component
public class ExternalApiRateLimiter {
    public enum Service {
        WIKIDATA("Wikidata"), WIKIDATA_QUERY("Wikidata 지역 조회"), WIKIPEDIA("Wikipedia"), COMMONS("Wikimedia Commons");

        private final String label;

        Service(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 요청 한 번 안에서의 시도 수(첫 시도 포함). */
    static final int MAX_ATTEMPTS = 3;
    static final Duration MAX_INLINE_WAIT = Duration.ofSeconds(15);
    static final Duration BASE_BACKOFF = Duration.ofSeconds(2);
    static final Duration MAX_BACKOFF = Duration.ofSeconds(60);
    /** 비정상적으로 긴 Retry-After 는 이 시간까지만 막는다. */
    static final Duration MAX_RETRY_AFTER = Duration.ofMinutes(10);

    /** 외부 API가 "잠시 뒤 다시"라고 답했음을 알리는 신호. retryAfter 는 없을 수 있다. */
    public static final class Limited extends RuntimeException {
        private final transient Duration retryAfter;

        public Limited(Duration retryAfter) {
            super(null, null, false, false);
            this.retryAfter = retryAfter;
        }

        public Duration retryAfter() {
            return retryAfter;
        }
    }

    /** 재시도를 다 써도 제한이 풀리지 않은 실패. 서비스별 예외(WikidataApiException 등)가 함께 구현한다. */
    public interface RateLimited {
        Service service();

        /** 이 서비스를 다시 부를 수 있을 때까지 남은 시간. */
        Duration retryAfter();
    }

    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private record State(Instant blockedUntil, int consecutive) {
    }

    private final Clock clock;
    private final Sleeper sleeper;
    private final DoubleSupplier random;
    private final int maxAttempts;
    private final Map<Service, State> states = new EnumMap<>(Service.class);

    public ExternalApiRateLimiter() {
        this(Clock.systemUTC(), duration -> Thread.sleep(duration.toMillis()),
                () -> ThreadLocalRandom.current().nextDouble(), MAX_ATTEMPTS);
    }

    ExternalApiRateLimiter(Clock clock, Sleeper sleeper, DoubleSupplier random, int maxAttempts) {
        this.clock = clock;
        this.sleeper = sleeper;
        this.random = random;
        this.maxAttempts = maxAttempts;
    }

    /** 단위 테스트용 클라이언트가 쓰는 한 번만 시도하는 대기 관리자. 기다리지 않는다. */
    static ExternalApiRateLimiter singleAttempt() {
        return new ExternalApiRateLimiter(Clock.systemUTC(), duration -> { }, () -> 0.5, 1);
    }

    /**
     * 서비스 대기 상태를 지키며 요청한다. request 가 {@link Limited}를 던지면 대기 시각을 정하고 한도 안에서 다시 시도한다.
     *
     * @param exhausted 한도를 넘었을 때 던질 서비스별 제한 예외(남은 대기 시간을 받는다)
     */
    public <T> T call(Service service, Supplier<T> request, Function<Duration, RuntimeException> exhausted) {
        Duration waited = Duration.ZERO;
        for (int attempt = 1; ; attempt++) {
            Duration wait = remaining(service);
            if (wait.compareTo(Duration.ZERO) > 0) {
                if (waited.plus(wait).compareTo(MAX_INLINE_WAIT) > 0) throw exhausted.apply(wait);
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw exhausted.apply(remaining(service));
                }
                waited = waited.plus(wait);
            }
            try {
                T result = request.get();
                recordSuccess(service);
                return result;
            } catch (Limited limited) {
                Duration delay = recordLimited(service, limited.retryAfter());
                if (attempt >= maxAttempts) throw exhausted.apply(delay);
            }
        }
    }

    /** 이 서비스를 다시 부를 수 있을 때까지 남은 시간. 막혀 있지 않으면 0. */
    public Duration remaining(Service service) {
        synchronized (states) {
            State state = states.get(service);
            if (state == null) return Duration.ZERO;
            Duration left = Duration.between(clock.instant(), state.blockedUntil());
            return left.isNegative() ? Duration.ZERO : left;
        }
    }

    Duration recordLimited(Service service, Duration retryAfter) {
        synchronized (states) {
            Instant now = clock.instant();
            State state = states.getOrDefault(service, new State(Instant.EPOCH, 0));
            int consecutive = state.consecutive() + 1;
            Duration delay = retryAfter != null && retryAfter.compareTo(Duration.ZERO) > 0
                    ? min(retryAfter, MAX_RETRY_AFTER) : backoff(consecutive);
            Instant until = now.plus(delay);
            if (state.blockedUntil().isAfter(until)) until = state.blockedUntil();
            states.put(service, new State(until, consecutive));
            return Duration.between(now, until);
        }
    }

    private void recordSuccess(Service service) {
        synchronized (states) {
            State state = states.get(service);
            if (state != null && state.consecutive() != 0) states.put(service, new State(state.blockedUntil(), 0));
        }
    }

    private Duration backoff(int consecutive) {
        long base = BASE_BACKOFF.toMillis() * (1L << Math.min(consecutive - 1, 10));
        long capped = Math.min(base, MAX_BACKOFF.toMillis());
        double factor = 0.8 + 0.4 * random.getAsDouble();
        return Duration.ofMillis(Math.min((long) (capped * factor), MAX_BACKOFF.toMillis()));
    }

    /** Retry-After 헤더 값(초 또는 HTTP 날짜). 해석할 수 없으면 null. */
    public static Duration parseRetryAfter(String value, Clock clock) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.strip();
        try {
            long seconds = Long.parseLong(trimmed);
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            // HTTP 날짜 형식일 수 있다.
        }
        try {
            Duration left = Duration.between(clock.instant(),
                    ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            return left.isNegative() ? Duration.ZERO : left;
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    public static Duration parseRetryAfter(String value) {
        return parseRetryAfter(value, Clock.systemUTC());
    }

    /** 예외(원인 포함) 안에 요청 제한 실패가 있으면 돌려준다. */
    public static Optional<RateLimited> rateLimitOf(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 10; depth++, current = current.getCause()) {
            if (current instanceof RateLimited limited) return Optional.of(limited);
        }
        return Optional.empty();
    }

    private static Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }
}
