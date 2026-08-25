package com.example.travlediary.service.travelplan;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 지금 어느 방에 누가 붙어 있는지만 들고 있는 메모리 장부.
 * DB 에 저장하지 않으므로 서버가 다시 뜨면 비어 있고, 브라우저가 다시 붙으면 다시 채워진다.
 *
 * <p>한 사람이 탭을 여러 개 열 수 있어 참여자 단위가 아니라 <b>연결 단위</b>로 센다.
 * 마지막 연결이 끊어질 때만 접속이 끊긴 것으로 본다.
 */
@Service
public class TravelPlanPresenceService {

    /** 방 -> 참여자 -> 그 참여자의 연결들 */
    private final Map<Long, Map<Long, Set<String>>> roomMembers = new ConcurrentHashMap<>();
    /** 연결 -> 어느 방의 누구였는지. 끊어질 때 이 표만 보고 정리한다. */
    private final Map<String, PresenceKey> sessions = new ConcurrentHashMap<>();

    /** 연결 하나가 가리키는 자리. */
    public record PresenceKey(Long travelPlanId, Long memberId) {
    }

    /**
     * 연결 하나를 그 방의 참여자 자리에 올린다.
     * 같은 연결이 두 번 들어와도 집합이라 수가 어긋나지 않는다.
     */
    public void join(Long travelPlanId, Long memberId, String sessionId) {
        if (travelPlanId == null || memberId == null || sessionId == null) {
            return;
        }
        PresenceKey key = new PresenceKey(travelPlanId, memberId);

        // 같은 연결이 다른 자리에 남아 있었다면 먼저 걷어낸다.
        PresenceKey previous = sessions.put(sessionId, key);
        if (previous != null && !previous.equals(key)) {
            detach(previous, sessionId);
        }

        roomMembers
                .computeIfAbsent(travelPlanId, plan -> new ConcurrentHashMap<>())
                .computeIfAbsent(memberId, member -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
    }

    /**
     * 연결이 끊겼다. 그 사람의 남은 연결이 없을 때만 접속이 끊긴 것이 된다.
     *
     * @return 알려야 할 방. 모르는 연결이면 비어 있는 결과(오류로 만들지 않는다)
     */
    public Optional<Long> leave(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        PresenceKey key = sessions.remove(sessionId);
        if (key == null) {
            return Optional.empty();
        }
        detach(key, sessionId);
        return Optional.of(key.travelPlanId());
    }

    /** 그 방에서 지금 붙어 있는 참여자들. 방마다 따로 센다. */
    public List<Long> onlineMemberIds(Long travelPlanId) {
        Map<Long, Set<String>> members = travelPlanId == null
                ? null : roomMembers.get(travelPlanId);
        if (members == null) {
            return List.of();
        }
        return members.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** 그 참여자가 지금 붙어 있는 연결 수. 탭을 몇 개 열어 뒀는지에 해당한다. */
    public int sessionCount(Long travelPlanId, Long memberId) {
        Map<Long, Set<String>> members = travelPlanId == null
                ? null : roomMembers.get(travelPlanId);
        if (members == null) {
            return 0;
        }
        Set<String> memberSessions = members.get(memberId);
        return memberSessions == null ? 0 : memberSessions.size();
    }

    /** 연결 하나를 자리에서 뺀다. 빈 자리와 빈 방은 남기지 않는다. */
    private void detach(PresenceKey key, String sessionId) {
        roomMembers.computeIfPresent(key.travelPlanId(), (travelPlanId, members) -> {
            members.computeIfPresent(key.memberId(), (memberId, memberSessions) -> {
                memberSessions.remove(sessionId);
                // 마지막 연결이 빠지면 그 사람은 접속이 끊긴 것이다.
                return memberSessions.isEmpty() ? null : memberSessions;
            });
            return members.isEmpty() ? null : members;
        });
    }
}
