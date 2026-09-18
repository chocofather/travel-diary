package com.example.travlediary.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * 고정 창(fixed window) 요청 횟수 제한. 이 인스턴스의 메모리에만 남는다.
 *
 * <p>같은 모양의 {@code ConcurrentHashMap} 계수기가 기능마다 복붙되지 않도록 한 자리에 모은다.
 * 번역 요청 제한({@code InMemoryTranslationRateLimiter})이 쓰던 창 계수 방식과 로그인 제한
 * ({@link LoginThrottle})이 쓰던 용량 정리 방식을 합친 것이고, 정책(몇 번/몇 분)은 부르는 쪽이 정한다.
 *
 * <p>키는 무한히 쌓이지 않는다. 창이 끝난 항목은 주기적으로 지우고, 그래도 늘어나면 가장 오래
 * 쓰이지 않은 키부터 버린다. (버려진 키는 다음 요청에서 새 창으로 시작한다)
 *
 * <p>서버를 다시 띄우면 상태가 사라진다. 단일 인스턴스 단계에서는 그것을 허용한다.
 * 여러 대로 늘릴 때는 이 클래스를 공유 저장소(Redis 등) 구현으로 바꾸면 되고,
 * 부르는 쪽은 {@link AccountAbuseGuard} 하나만 보므로 함께 고칠 자리가 없다.
 */
public class FixedWindowRateLimiter {

    /** 이만큼 다룰 때마다 끝난 창을 한 번 쓸어낸다. (요청마다 전체를 훑지 않는다) */
    private static final int CLEANUP_INTERVAL = 256;
    /** 키 하나가 차지하는 메모리를 묶어 두기 위한 상한. */
    private static final int MAX_KEY_LENGTH = 160;

    private final Clock clock;
    private final int maxTrackedKeys;
    /** 접근 순서 Map 이라 오래 쓰이지 않은 키가 앞에 모인다. */
    private final LinkedHashMap<String, Window> windows = new LinkedHashMap<>(16, 0.75f, true);
    private int operationsSinceCleanup;

    public FixedWindowRateLimiter(Clock clock, int maxTrackedKeys) {
        this.clock = clock;
        this.maxTrackedKeys = maxTrackedKeys;
    }

    /**
     * 요청 한 번을 센다.
     *
     * @param limit  한 창에서 허용할 횟수
     * @param window 창의 길이
     * @return 허용 여부와, 막혔다면 다시 시도할 수 있을 때까지 남은 시간
     */
    public synchronized RateLimitDecision acquire(String key, int limit, Duration window) {
        Instant now = clock.instant();
        cleanUpIfNeeded(now);

        String normalizedKey = normalize(key);
        Window state = windows.get(normalizedKey);
        if (state == null || !now.isBefore(state.endsAt)) {
            state = new Window(now.plus(window));
            windows.put(normalizedKey, state);
        }

        if (state.count >= limit) {
            boolean firstRejection = !state.rejectionReported;
            state.rejectionReported = true;
            return RateLimitDecision.reject(Duration.between(now, state.endsAt), firstRejection);
        }

        state.count++;
        trimToCapacity();
        return RateLimitDecision.pass();
    }

    private void cleanUpIfNeeded(Instant now) {
        operationsSinceCleanup++;
        if (operationsSinceCleanup < CLEANUP_INTERVAL) {
            return;
        }
        operationsSinceCleanup = 0;
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().endsAt));
    }

    /** 끝난 창을 지워도 남아 있으면 가장 오래 쓰이지 않은 키부터 버린다. */
    private void trimToCapacity() {
        Iterator<String> iterator = windows.keySet().iterator();
        while (windows.size() > maxTrackedKeys && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private String normalize(String key) {
        if (key == null || key.isBlank()) {
            return "<unknown>";
        }
        String stripped = key.strip();
        return stripped.length() <= MAX_KEY_LENGTH
                ? stripped
                : stripped.substring(0, MAX_KEY_LENGTH);
    }

    private static final class Window {
        private final Instant endsAt;
        private int count;
        /** 이 창에서 거절 로그를 이미 남겼는지. 한 창에 한 줄만 남기려는 표시다. */
        private boolean rejectionReported;

        private Window(Instant endsAt) {
            this.endsAt = endsAt;
        }
    }
}
