package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanEditorEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 완료된 뒤의 뒷정리.
 *
 * <p>반드시 커밋이 끝난 뒤에만 한다.
 * 트랜잭션 안에서 먼저 지우면, 뒤에서 실패해 완료가 되돌아갔을 때
 * 남의 작성 중 내용까지 사라진다.
 *
 * <p>하는 일은 둘이다.
 * 붙잡혀 있던 자리를 모두 놓고, 그 사실을 방 전체에 알린다.
 * 늦게 도착하는 저장은 방 상태가 이미 COMPLETED 라 서버에서 걸린다.
 */
@Component
@RequiredArgsConstructor
public class TravelPlanCompletedListener {

    private final TravelPlanEditorRealtimeService travelPlanEditorRealtimeService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlanCompleted(TravelPlanCompletedEvent event) {
        Long travelPlanId = event.travelPlanId();

        /*
          붙잡혀 있던 자리를 놓고, 놓인 자리마다 알린다.
          그래야 다른 화면의 "편집 중" 표시가 남지 않는다.
        */
        travelPlanEditorRealtimeService.releaseAllByPlan(travelPlanId)
                .forEach(lock -> simpMessagingTemplate.convertAndSend(
                        TravelPlanEditorDestinations.topic(travelPlanId),
                        TravelPlanEditorEventDto.unlocked(lock.toDto())));

        // 이제 이 방은 고칠 수 없다. 새로고침 없이 모두가 알게 한다.
        simpMessagingTemplate.convertAndSend(
                TravelPlanScheduleDestinations.topic(travelPlanId),
                Map.of("type", "PLAN_COMPLETED",
                        "travelPlanId", travelPlanId,
                        "affectedDayIds", java.util.List.of()));
    }
}
