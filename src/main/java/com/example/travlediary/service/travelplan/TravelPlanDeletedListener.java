package com.example.travlediary.service.travelplan;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * 방이 사라진 뒤의 뒷정리.
 *
 * <p>반드시 커밋이 끝난 뒤에만 한다. 되돌아간 삭제로 사람들을 내보내지 않기 위해서다.
 *
 * <p>하는 일은 둘이다. 서버가 들고 있던 편집 자리를 놓고,
 * 이 방을 보고 있는 사람들에게 방이 없어졌다고 한 번 알린다.
 *
 * <p>사람마다 연결을 끊지 않는다. 알림을 받은 화면이 스스로 목록으로 나가고,
 * 그때 연결도 함께 정리된다. 남아 있는 연결로 무엇을 보내더라도
 * 참여 기록이 사라진 뒤라 실시간 쪽 검사에서 걸린다.
 */
@Component
@RequiredArgsConstructor
public class TravelPlanDeletedListener {

    private final TravelPlanEditorRealtimeService travelPlanEditorRealtimeService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlanDeleted(TravelPlanDeletedEvent event) {
        Long travelPlanId = event.travelPlanId();

        /*
          붙잡혀 있던 자리를 놓는다. 완료 때와 달리 자리마다 알리지 않는다.
          그 자리를 보고 있던 화면은 곧 이 방을 떠나므로 알려 줄 곳이 없고,
          그대로 두면 사라진 방의 편집 상태가 서버에 남는다.
        */
        travelPlanEditorRealtimeService.releaseAllByPlan(travelPlanId);

        /*
          완료(PLAN_COMPLETED)와 같은 통로를 쓴다.
          방을 열어 둔 사람은 모두 이 통로를 이미 듣고 있어 새로 만들 것이 없다.
          다만 완료와 뜻이 다르므로 이름은 따로 둔다 —
          완료된 여행은 최종본으로 남지만 지워진 여행은 남지 않는다.
        */
        simpMessagingTemplate.convertAndSend(
                TravelPlanScheduleDestinations.topic(travelPlanId),
                Map.of("type", "PLAN_DELETED",
                        "travelPlanId", travelPlanId,
                        "affectedDayIds", List.of()));
    }
}
