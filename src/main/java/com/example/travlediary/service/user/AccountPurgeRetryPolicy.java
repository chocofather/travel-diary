package com.example.travlediary.service.user;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 후처리 task 의 재시도 간격과 포기 기준.
 *
 * <p>간격은 예측할 수 있게 단계로 고정한다. 잦은 재시도로 파일시스템을 두드리지 않고,
 * 일시적인 장애라면 하루 안에 여러 번 기회를 준다.
 */
public final class AccountPurgeRetryPolicy {

    /** 시도 횟수별 다음 재시도까지의 간격. 목록을 넘어서면 마지막 값을 계속 쓴다. */
    private static final List<Duration> BACKOFFS = List.of(
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(12),
            Duration.ofHours(24));

    /** 이 횟수만큼 시도해도 실패하면 자동 재시도를 멈추고 운영 점검 대상으로 남긴다. */
    public static final int MAX_ATTEMPTS = 10;

    /**
     * task 를 집어 든 뒤 결과를 기록할 때까지 다른 worker 가 같은 task 를 집지 않게 하는 시간.
     * 이 시간이 지나면 처리 중 죽은 task 도 다시 대상이 된다.
     */
    public static final Duration CLAIM_LEASE = Duration.ofMinutes(5);

    private AccountPurgeRetryPolicy() {
    }

    /** @param attempts 이번 시도까지 포함한 누적 시도 횟수 */
    public static boolean exhausted(int attempts) {
        return attempts >= MAX_ATTEMPTS;
    }

    /** @param attempts 이번 시도까지 포함한 누적 시도 횟수 */
    public static LocalDateTime nextRetryAt(LocalDateTime currentTime, int attempts) {
        int index = Math.max(1, attempts) - 1;
        Duration backoff = BACKOFFS.get(Math.min(index, BACKOFFS.size() - 1));
        return currentTime.plus(backoff);
    }
}
