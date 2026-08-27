package com.example.travlediary.service.travelplan;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 어느 연결이 어느 방을 보고 있는지 들고 있다가, 자격을 잃은 연결을 끊는다.
 *
 * <p>구독은 SUBSCRIBE 한 번만 검사된다. 그 뒤로는 브로커가 그 연결에 계속 보내므로,
 * 내보내진 사람이 화면을 열어 둔 채 두면 그때부터의 채팅·일정·투표를 계속 받는다.
 * 클라이언트가 알아서 나가 주기를 기대하지 않고 여기서 연결 자체를 끊는다.
 *
 * <p>접속 표시(presence)와는 다른 장부다.
 * 저쪽은 "지금 몇 명이 보고 있는가" 를 세는 화면용 값이고,
 * 이쪽은 "이 연결을 끊어야 하는가" 를 가리는 보안용이다.
 * 그래서 접속 인사(presence/join)가 아니라 구독이 받아들여진 시점에 적는다.
 * 인사보다 구독이 먼저이므로 그 사이에 자격을 잃어도 빠뜨리지 않는다.
 *
 * <p>메모리에만 둔다. 서버가 다시 뜨면 연결도 함께 끊어져 비어 있는 것이 맞다.
 */
@Service
public class TravelPlanRoomSessionRegistry {

    /** 연결 번호 -> 그 연결 자체. 끊으려면 연결을 들고 있어야 한다. */
    private final Map<String, WebSocketSession> connections = new ConcurrentHashMap<>();
    /** 방 -> 그 방 참여자 -> 그 참여자가 열어 둔 연결들 */
    private final Map<Long, Map<Long, Set<String>>> roomSessions = new ConcurrentHashMap<>();

    /** 새 연결이 붙었다. 아직 어느 방을 보는지는 모른다. */
    public void register(WebSocketSession session) {
        if (session == null || session.getId() == null) {
            return;
        }
        connections.put(session.getId(), session);
    }

    /**
     * 이 연결이 그 방을 보기 시작했다. 구독이 받아들여진 뒤에만 부른다.
     *
     * @param memberId travel_plan_members.id. 방 안에서만 뜻이 있는 값이라
     *                 다른 방의 같은 사람과 섞이지 않는다.
     */
    public void watching(Long travelPlanId, Long memberId, String sessionId) {
        if (travelPlanId == null || memberId == null || sessionId == null) {
            return;
        }
        roomSessions
                .computeIfAbsent(travelPlanId, plan -> new ConcurrentHashMap<>())
                .computeIfAbsent(memberId, member -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
    }

    /**
     * 그 방에서 그 사람이 열어 둔 연결들.
     * 끊기 직전에 그 연결에만 한 줄 보내려고 먼저 물어볼 때 쓴다.
     */
    public List<String> sessionsOf(Long travelPlanId, Long memberId) {
        if (travelPlanId == null || memberId == null) {
            return List.of();
        }
        Map<Long, Set<String>> members = roomSessions.get(travelPlanId);
        Set<String> sessionIds = members == null ? null : members.get(memberId);
        return sessionIds == null ? List.of() : List.copyOf(sessionIds);
    }

    /**
     * 그 방에서 그 사람이 열어 둔 연결을 모두 끊는다.
     *
     * <p>탭을 여러 개 열어 두었으면 전부 끊긴다.
     * 방과 참여자로 좁혀 찾으므로 다른 방을 보고 있는 연결이나
     * 같은 방의 다른 사람 연결은 건드리지 않는다.
     *
     * @return 방금 끊은 연결 번호들. 부른 쪽이 이 번호로 뒷정리를 이어 간다.
     */
    public List<String> disconnect(Long travelPlanId, Long memberId) {
        if (travelPlanId == null || memberId == null) {
            return List.of();
        }
        Map<Long, Set<String>> members = roomSessions.get(travelPlanId);
        Set<String> sessionIds = members == null ? null : members.remove(memberId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        roomSessions.computeIfPresent(travelPlanId,
                (plan, remaining) -> remaining.isEmpty() ? null : remaining);

        List<String> closed = List.copyOf(sessionIds);
        closed.forEach(this::close);
        return closed;
    }

    /** 연결이 끊어졌다(스스로든 우리가 끊었든). 장부에서 지운다. */
    public void forget(String sessionId) {
        if (sessionId == null) {
            return;
        }
        connections.remove(sessionId);
        roomSessions.forEach((travelPlanId, members) ->
                members.values().forEach(sessions -> sessions.remove(sessionId)));
        roomSessions.values().removeIf(members -> {
            members.values().removeIf(Set::isEmpty);
            return members.isEmpty();
        });
    }

    /** 이 연결이 그 방을 보고 있는 것으로 적혀 있는지. */
    public boolean isWatching(Long travelPlanId, Long memberId, String sessionId) {
        Map<Long, Set<String>> members = roomSessions.get(travelPlanId);
        Set<String> sessions = members == null ? null : members.get(memberId);
        return sessions != null && sessions.contains(sessionId);
    }

    private void close(String sessionId) {
        WebSocketSession session = connections.remove(sessionId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            // 오류가 아니라 정책상 닫는 것이라 평범한 종료로 알린다.
            session.close(CloseStatus.NORMAL);
        } catch (IOException | IllegalStateException exception) {
            // 이미 닫히는 중일 수 있다. 끊는 것이 목적이므로 여기서 멈추지 않는다.
        }
    }
}
