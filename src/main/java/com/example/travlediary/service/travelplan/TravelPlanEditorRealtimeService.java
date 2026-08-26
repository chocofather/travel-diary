package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanEditorLockDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 지금 누가 어느 자리를 붙잡고 무엇을 쓰고 있는지만 들고 있는 메모리 장부.
 *
 * <p>DB 에 저장하지 않는다. 작성 중 내용은 임시일 뿐이고,
 * 저장은 언제나 기존 HTTP 경로로 travel_plan_items 에 들어간다.
 *
 * <p>자리를 잡는 단위는 사람이 아니라 <b>연결(session)</b> 이다.
 * 같은 사람이 탭을 두 개 열어도 한 자리는 한 연결만 붙잡는다.
 */
@Service
public class TravelPlanEditorRealtimeService {

    /** 새 일정 자리는 DAY 마다 하나뿐이다. */
    public static final String ADD_MODE = "ADD";
    /** 기존 일정은 줄마다 따로 잡는다. */
    public static final String EDIT_MODE = "EDIT";
    /** 새 대안 자리는 A 일정마다 하나뿐이다(B 인지 C 인지는 서버가 정한다). */
    public static final String ALT_ADD_MODE = "ALT_ADD";
    /** 기존 대안은 B/C 마다 따로 잡는다. */
    public static final String ALT_EDIT_MODE = "ALT_EDIT";

    /** 방 -> 자리 -> 붙잡고 있는 연결 */
    private final Map<Long, Map<String, EditorLock>> roomLocks = new ConcurrentHashMap<>();
    /** 연결 -> 그 연결이 붙잡은 자리들. 끊어질 때 이 표만 보고 정리한다. */
    private final Map<String, Set<RoomKey>> sessionLocks = new ConcurrentHashMap<>();

    /** 방 안의 한 자리. */
    public record RoomKey(Long travelPlanId, String lockKey) {
    }

    /**
     * 붙잡고 있는 연결과 그 자리에서 쓰고 있는 내용.
     * 대안은 조건과 내용 두 칸이라 조건도 함께 들고 다닌다(A 일정에서는 null).
     */
    public record EditorLock(
            Long travelPlanId,
            String lockKey,
            String sessionId,
            String mode,
            Long dayId,
            Long itemId,
            Long alternativeId,
            Long memberId,
            String displayName,
            String conditionLabel,
            String content) {

        public TravelPlanEditorLockDto toDto() {
            return new TravelPlanEditorLockDto(lockKey, mode, dayId, itemId, alternativeId,
                    conditionLabel, content, memberId, displayName);
        }

        EditorLock withDraft(String newConditionLabel, String newContent) {
            return new EditorLock(travelPlanId, lockKey, sessionId, mode, dayId, itemId,
                    alternativeId, memberId, displayName, newConditionLabel, newContent);
        }
    }

    /** 새 일정 자리의 이름. DAY 마다 하나다. */
    public static String addLockKey(Long dayId) {
        return ADD_MODE + ":" + dayId;
    }

    /** 기존 일정 자리의 이름. */
    public static String editLockKey(Long itemId) {
        return "ITEM:" + itemId;
    }

    /** 새 대안 자리의 이름. A 일정마다 하나다. */
    public static String alternativeAddLockKey(Long itemId) {
        return ALT_ADD_MODE + ":" + itemId;
    }

    /** 기존 대안 자리의 이름. */
    public static String alternativeEditLockKey(Long alternativeId) {
        return "ALT:" + alternativeId;
    }

    /**
     * 그 자리를 붙잡는다. 이미 누가 붙잡고 있으면 실패한다.
     * 같은 사람의 다른 탭이라도 마찬가지다.
     *
     * @return 성공하면 잡은 자리, 이미 누가 쓰고 있으면 빈 결과
     */
    public Optional<EditorLock> tryAcquire(EditorLock request) {
        if (request == null || request.travelPlanId() == null
                || request.lockKey() == null || request.sessionId() == null) {
            return Optional.empty();
        }
        // 화면과 같은 약속을 서버에서도 지킨다: 한 연결은 한 자리만 붙잡는다.
        // 편집기를 열지 못한 채 다음 자리를 눌러도 잠금이 쌓이지 않는다.
        releasedWhenAcquiring(request.travelPlanId(), request.sessionId(), request.lockKey());

        Map<String, EditorLock> locks =
                roomLocks.computeIfAbsent(request.travelPlanId(), plan -> new ConcurrentHashMap<>());
        // 한 자리에 한 연결만 들어가도록 원자적으로 넣는다.
        EditorLock existing = locks.putIfAbsent(request.lockKey(), request);
        if (existing != null) {
            return Optional.empty();
        }

        sessionLocks
                .computeIfAbsent(request.sessionId(), session -> ConcurrentHashMap.newKeySet())
                .add(new RoomKey(request.travelPlanId(), request.lockKey()));
        return Optional.of(request);
    }

    /**
     * 작성 중 내용을 갈아 끼운다.
     * 그 자리를 붙잡고 있는 연결만 바꿀 수 있다(남의 자리에 쓸 수 없다).
     *
     * @return 반영된 상태, 자리를 붙잡고 있지 않으면 빈 결과
     */
    public Optional<EditorLock> updateDraft(Long travelPlanId, String lockKey, String sessionId,
                                            String conditionLabel, String content) {
        Map<String, EditorLock> locks = travelPlanId == null ? null : roomLocks.get(travelPlanId);
        if (locks == null || lockKey == null || sessionId == null) {
            return Optional.empty();
        }
        EditorLock current = locks.get(lockKey);
        if (current == null || !current.sessionId().equals(sessionId)) {
            return Optional.empty();
        }
        // 조건과 내용은 늘 함께 온다. 상대 화면이 두 칸을 같은 시점의 값으로 본다.
        EditorLock updated = current.withDraft(conditionLabel, content);
        // 그 사이 자리가 바뀌지 않았을 때만 반영한다.
        return locks.replace(lockKey, current, updated) ? Optional.of(updated) : Optional.empty();
    }

    /**
     * 자리를 놓는다. 붙잡고 있던 연결만 놓을 수 있다.
     *
     * @return 실제로 놓인 자리, 아니면 빈 결과
     */
    public Optional<EditorLock> release(Long travelPlanId, String lockKey, String sessionId) {
        Map<String, EditorLock> locks = travelPlanId == null ? null : roomLocks.get(travelPlanId);
        if (locks == null || lockKey == null || sessionId == null) {
            return Optional.empty();
        }
        EditorLock lock = locks.get(lockKey);
        if (lock == null || !lock.sessionId().equals(sessionId)) {
            return Optional.empty();
        }
        locks.remove(lockKey, lock);
        forgetSessionKey(sessionId, new RoomKey(travelPlanId, lockKey));
        cleanUpRoom(travelPlanId);
        return Optional.of(lock);
    }

    /**
     * 연결이 끊겼다. 그 연결이 붙잡고 있던 자리를 모두 놓는다.
     * 다른 연결이 붙잡은 자리는 건드리지 않는다.
     *
     * @return 놓인 자리들. 각각 그 방에 알려야 한다.
     */
    public List<EditorLock> releaseAllBySession(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        Set<RoomKey> keys = sessionLocks.remove(sessionId);
        if (keys == null) {
            return List.of();
        }

        List<EditorLock> released = new ArrayList<>();
        keys.forEach(key -> {
            Map<String, EditorLock> locks = roomLocks.get(key.travelPlanId());
            if (locks == null) {
                return;
            }
            EditorLock lock = locks.get(key.lockKey());
            if (lock != null && lock.sessionId().equals(sessionId)) {
                locks.remove(key.lockKey(), lock);
                released.add(lock);
            }
            cleanUpRoom(key.travelPlanId());
        });
        return released;
    }

    /**
     * 같은 연결이 그 방에서 붙잡고 있던 다른 자리를 놓는다.
     * 새 자리를 잡을 때 먼저 불러 한 연결이 여러 자리를 들고 있지 않게 한다.
     *
     * @param keepLockKey 지금 잡으려는 자리. 이것만 남긴다.
     * @return 놓인 자리들. 각각 그 방에 알려야 다른 화면의 표시가 사라진다.
     */
    public List<EditorLock> releasedWhenAcquiring(Long travelPlanId, String sessionId,
                                                  String keepLockKey) {
        Map<String, EditorLock> locks = travelPlanId == null ? null : roomLocks.get(travelPlanId);
        if (locks == null || sessionId == null) {
            return List.of();
        }

        List<EditorLock> released = new ArrayList<>();
        locks.values().stream()
                .filter(lock -> lock.sessionId().equals(sessionId))
                .filter(lock -> !lock.lockKey().equals(keepLockKey))
                .toList()
                .forEach(lock -> {
                    if (locks.remove(lock.lockKey(), lock)) {
                        forgetSessionKey(sessionId, new RoomKey(travelPlanId, lock.lockKey()));
                        released.add(lock);
                    }
                });
        cleanUpRoom(travelPlanId);
        return released;
    }

    /** 그 방에서 지금 붙잡혀 있는 자리들. 끊겼다 다시 붙은 화면을 맞출 때 쓴다. */
    public List<TravelPlanEditorLockDto> locksOf(Long travelPlanId) {
        Map<String, EditorLock> locks = travelPlanId == null ? null : roomLocks.get(travelPlanId);
        if (locks == null) {
            return List.of();
        }
        return locks.values().stream().map(EditorLock::toDto).toList();
    }

    /** 그 자리를 지금 붙잡고 있는 연결. 확인용이다. */
    public Optional<EditorLock> find(Long travelPlanId, String lockKey) {
        Map<String, EditorLock> locks = travelPlanId == null ? null : roomLocks.get(travelPlanId);
        return locks == null ? Optional.empty() : Optional.ofNullable(locks.get(lockKey));
    }

    private void forgetSessionKey(String sessionId, RoomKey key) {
        sessionLocks.computeIfPresent(sessionId, (session, keys) -> {
            keys.remove(key);
            return keys.isEmpty() ? null : keys;
        });
    }

    private void cleanUpRoom(Long travelPlanId) {
        roomLocks.computeIfPresent(travelPlanId, (plan, locks) -> locks.isEmpty() ? null : locks);
    }
}
