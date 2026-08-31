package com.example.travlediary.service.diary;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * PIN 잠금을 푼 상태를 들고 있는 자리. 세션 하나에만 남는다.
 *
 * <p>세션에 두는 것은 <b>푼 다이어리 번호</b>와 <b>다이어리별 실패 횟수</b>뿐이다.
 * PIN 원문도, 해시도, 다이어리 객체도 담지 않는다.
 *
 * <p>잠금은 다이어리 한 권 단위다. A 를 풀었다고 B 까지 풀리지 않는다.
 * 로그아웃하거나 세션이 끝나면 함께 사라지므로 다음에 다시 확인해야 한다.
 */
@Component
public class DiaryPinSession {

    /** 이 세션에서 잠금을 푼 다이어리 번호 */
    private static final String UNLOCKED_KEY = "diaryPinUnlockedIds";
    /** 다이어리별 PIN 실패 기록 */
    private static final String ATTEMPTS_KEY = "diaryPinAttempts";

    /** 이만큼 잇달아 틀리면 잠시 쉬어 간다. (네 자리라 경우의 수가 적다) */
    static final int MAX_ATTEMPTS = 5;
    /** 쉬어 가는 시간. 사람이 다시 시도하기에는 짧고, 기계가 훑기에는 긴 정도다. */
    static final Duration BLOCK = Duration.ofMinutes(1);

    /** 이 세션에서 그 다이어리의 잠금이 풀려 있는지 */
    public boolean isUnlocked(HttpSession session, Long diaryId) {
        return session != null && diaryId != null && unlocked(session).contains(diaryId);
    }

    /** 잠금을 푼다. (PIN 이 맞았을 때만 부른다) */
    public void unlock(HttpSession session, Long diaryId) {
        if (session == null || diaryId == null) {
            return;
        }
        Set<Long> ids = unlocked(session);
        ids.add(diaryId);
        session.setAttribute(UNLOCKED_KEY, ids);
    }

    /** 다시 잠근다. (PIN 을 없앴거나 다시 걸었을 때 쓴다) */
    public void lock(HttpSession session, Long diaryId) {
        if (session == null || diaryId == null) {
            return;
        }
        Set<Long> ids = unlocked(session);
        if (ids.remove(diaryId)) {
            session.setAttribute(UNLOCKED_KEY, ids);
        }
    }

    /** 이 세션의 잠금 상태를 모두 지운다. */
    public void clear(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(UNLOCKED_KEY);
        session.removeAttribute(ATTEMPTS_KEY);
    }

    /** 지금 그 다이어리가 재시도 제한에 걸려 있는지 */
    public boolean isBlocked(HttpSession session, Long diaryId) {
        return remainingBlockSeconds(session, diaryId) > 0;
    }

    /** 다시 시도할 수 있을 때까지 남은 시간(초). 걸려 있지 않으면 0 이다. */
    public long remainingBlockSeconds(HttpSession session, Long diaryId) {
        if (session == null || diaryId == null) {
            return 0;
        }
        Attempts attempts = attempts(session).get(diaryId);
        if (attempts == null || attempts.blockedUntil <= 0) {
            return 0;
        }
        long remaining = attempts.blockedUntil - System.currentTimeMillis();
        return remaining > 0 ? (remaining + 999) / 1000 : 0;
    }

    /**
     * 틀린 횟수를 하나 센다. 정해진 횟수를 넘기면 잠시 쉬어 가도록 표시한다.
     * (횟수는 다이어리마다 따로 센다 — 다른 다이어리의 실패와 섞이지 않는다)
     */
    public void recordFailure(HttpSession session, Long diaryId) {
        if (session == null || diaryId == null) {
            return;
        }
        Map<Long, Attempts> all = attempts(session);
        Attempts attempts = all.getOrDefault(diaryId, new Attempts());
        // 쉬어 가는 시간이 지났으면 처음부터 다시 센다.
        if (attempts.blockedUntil > 0 && attempts.blockedUntil <= System.currentTimeMillis()) {
            attempts = new Attempts();
        }
        attempts.count++;
        if (attempts.count >= MAX_ATTEMPTS) {
            attempts.blockedUntil = System.currentTimeMillis() + BLOCK.toMillis();
            attempts.count = 0;
        }
        all.put(diaryId, attempts);
        session.setAttribute(ATTEMPTS_KEY, all);
    }

    /** 맞혔으면 그 다이어리의 실패 기록을 지운다. */
    public void resetFailures(HttpSession session, Long diaryId) {
        if (session == null || diaryId == null) {
            return;
        }
        Map<Long, Attempts> all = attempts(session);
        if (all.remove(diaryId) != null) {
            session.setAttribute(ATTEMPTS_KEY, all);
        }
    }

    @SuppressWarnings("unchecked")
    private Set<Long> unlocked(HttpSession session) {
        Object value = session.getAttribute(UNLOCKED_KEY);
        return value instanceof Set ? (Set<Long>) value : new LinkedHashSet<>();
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Attempts> attempts(HttpSession session) {
        Object value = session.getAttribute(ATTEMPTS_KEY);
        return value instanceof Map ? (Map<Long, Attempts>) value : new LinkedHashMap<>();
    }

    /** 다이어리 한 권의 실패 기록. 숫자 두 개뿐이라 PIN 을 되짚을 단서가 없다. */
    private static final class Attempts implements Serializable {
        private int count;
        private long blockedUntil;
    }
}
