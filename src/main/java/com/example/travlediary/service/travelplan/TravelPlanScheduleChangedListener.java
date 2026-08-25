package com.example.travlediary.service.travelplan;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 일정 변경을 같은 방의 다른 화면에 알린다.
 *
 * <p>반드시 커밋이 끝난 뒤에만 내보낸다. 트랜잭션 안에서 바로 보내면
 * 뒤늦게 롤백된 변경을 다른 사람이 먼저 보게 된다.
 * {@link TransactionPhase#AFTER_COMMIT} 이라 롤백되면 아무것도 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class TravelPlanScheduleChangedListener {

    private final SimpMessagingTemplate simpMessagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduleChanged(TravelPlanScheduleChangedEvent event) {
        simpMessagingTemplate.convertAndSend(
                TravelPlanScheduleDestinations.topic(event.travelPlanId()), event);
    }
}
