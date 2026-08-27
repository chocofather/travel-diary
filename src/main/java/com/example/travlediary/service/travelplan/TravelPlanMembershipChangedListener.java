package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanEditorEventDto;
import com.example.travlediary.dto.TravelPlanPresenceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * 참여자 명단이 바뀐 뒤의 뒷정리.
 *
 * <p>반드시 커밋이 끝난 뒤에만 한다.
 * 트랜잭션 안에서 먼저 보내면 뒤에서 실패해 되돌아갔을 때
 * 다른 화면에 없는 사람이 남거나, 멀쩡한 사람의 연결이 끊긴다.
 *
 * <p>하는 일은 둘이다.
 *
 * <ul>
 *   <li>방에서 빠진 사람이 있으면 그 사람의 연결을 끊는다.</li>
 *   <li>남은 사람들에게 명단이 바뀌었다고 알린다.</li>
 * </ul>
 *
 * <p>연결을 끊는 것이 핵심이다.
 * 구독은 SUBSCRIBE 한 번만 검사되므로, 내보내진 사람이 화면을 열어 둔 채 두면
 * 끊기 전까지 그때부터의 채팅·일정·투표를 계속 받는다.
 * 클라이언트가 스스로 나가 주기를 믿지 않는다.
 */
@Component
@RequiredArgsConstructor
public class TravelPlanMembershipChangedListener {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final TravelPlanRoomSessionRegistry travelPlanRoomSessionRegistry;
    private final TravelPlanEditorRealtimeService travelPlanEditorRealtimeService;
    private final TravelPlanPresenceService travelPlanPresenceService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipChanged(TravelPlanMembershipChangedEvent event) {
        Long travelPlanId = event.travelPlanId();

        // 자격을 잃은 사람이 있으면 먼저 내보낸다.
        if (event.revokedMemberId() != null) {
            revokeAccess(travelPlanId, event.revokedMemberId());
        }

        // 남은 사람들은 명단을 다시 읽는다. 사람 수는 싣지 않는다.
        simpMessagingTemplate.convertAndSend(
                TravelPlanMemberDestinations.topic(travelPlanId),
                Map.of("type", "MEMBERSHIP_CHANGED", "travelPlanId", travelPlanId));
    }

    /**
     * 그 사람이 이 방에 열어 둔 연결을 모두 끊고 흔적을 지운다.
     *
     * <p>탭을 여러 개 열어 두었으면 전부 끊긴다.
     * 방과 참여자로 좁혀 찾으므로 다른 방을 보고 있는 연결이나
     * 같은 방의 다른 사람 연결은 건드리지 않는다.
     */
    private void revokeAccess(Long travelPlanId, Long memberId) {
        // 끊기 전에 한 줄 알린다. 화면이 멈춘 채로 남지 않게 하는 안내일 뿐이다.
        notifyRevoked(travelPlanId, memberId);

        List<String> closed = travelPlanRoomSessionRegistry.disconnect(travelPlanId, memberId);
        if (closed.isEmpty()) {
            return;
        }

        for (String sessionId : closed) {
            /*
              그 연결이 붙잡고 있던 자리를 놓는다.
              놓지 않으면 남은 사람들 화면에 "편집 중" 표시가 영원히 남는다.
              (연결이 끊기면 SessionDisconnectEvent 로도 정리되지만,
               그 사건을 기다리지 않고 여기서 확실히 끝낸다)
            */
            travelPlanEditorRealtimeService.releaseAllBySession(sessionId)
                    .forEach(lock -> simpMessagingTemplate.convertAndSend(
                            TravelPlanEditorDestinations.topic(lock.travelPlanId()),
                            TravelPlanEditorEventDto.unlocked(lock.toDto())));

            // 접속 표시에서도 뺀다. "접속 중 N명" 이 곧바로 줄어든다.
            travelPlanPresenceService.leave(sessionId);
        }

        simpMessagingTemplate.convertAndSend(
                TravelPlanPresenceDestinations.topic(travelPlanId),
                TravelPlanPresenceDto.of(
                        travelPlanPresenceService.onlineMemberIds(travelPlanId)));
    }

    /**
     * 끊길 연결에만 한 줄 보낸다.
     *
     * <p>사람이 아니라 연결 하나하나를 지목한다.
     * 같은 사람이 다른 방을 보고 있는 탭까지 나가게 하면 안 되기 때문이다.
     */
    private void notifyRevoked(Long travelPlanId, Long memberId) {
        for (String sessionId : travelPlanRoomSessionRegistry.sessionsOf(travelPlanId, memberId)) {
            SimpMessageHeaderAccessor accessor =
                    SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            accessor.setSessionId(sessionId);
            accessor.setLeaveMutable(true);
            simpMessagingTemplate.convertAndSendToUser(
                    sessionId,
                    TravelPlanMemberDestinations.ACCESS_QUEUE,
                    Map.of("type", "ACCESS_REVOKED",
                            "travelPlanId", travelPlanId,
                            "message", "더 이상 이 여행 계획에 참여하고 있지 않습니다."),
                    accessor.getMessageHeaders());
        }
    }
}
