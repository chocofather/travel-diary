package com.example.travlediary.service.travelplan;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 참여자 명단이 바뀐 사실을 방 전체에 알린다.
 *
 * <p>반드시 커밋이 끝난 뒤에만 보낸다.
 * 트랜잭션 안에서 먼저 보내면 뒤에서 실패해 되돌아갔을 때
 * 다른 화면에 없는 사람이 남는다.
 *
 * <p>사람 수나 이름은 싣지 않는다. 받은 화면이 서버에서 최신 명단을 다시 읽는다.
 */
@Component
@RequiredArgsConstructor
public class TravelPlanMembershipChangedListener {

    private final SimpMessagingTemplate simpMessagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipChanged(TravelPlanMembershipChangedEvent event) {
        Long travelPlanId = event.travelPlanId();
        simpMessagingTemplate.convertAndSend(
                TravelPlanMemberDestinations.topic(travelPlanId),
                Map.of("type", "MEMBERSHIP_CHANGED", "travelPlanId", travelPlanId));
    }
}
