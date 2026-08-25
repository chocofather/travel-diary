package com.example.travlediary.service.travelplan;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 일정이 실제로 바뀌었다는 서버 내부 알림.
 *
 * <p>내용 자체는 싣지 않는다. "이 DAY 를 다시 읽어라" 는 신호일 뿐이고,
 * 화면에 그릴 값은 클라이언트가 서버에서 다시 조회한다. DB 가 계속 최종 기준이다.
 *
 * <p>Service 의 사용자 동작 하나에 이 이벤트도 하나만 발행한다.
 * (안에서 UPDATE 가 여러 번 일어나도 마찬가지다)
 */
public record TravelPlanScheduleChangedEvent(
        Long travelPlanId,
        List<Long> affectedDayIds,
        TravelPlanScheduleChangeType changeType) {

    public TravelPlanScheduleChangedEvent {
        // 같은 DAY 가 두 번 들어와도 한 번만 남긴다.
        affectedDayIds = affectedDayIds == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(affectedDayIds));
    }

    /** 한 DAY 만 바뀐 경우(추가/수정/삭제/위·아래 이동). */
    public static TravelPlanScheduleChangedEvent ofDay(
            Long travelPlanId, Long dayId, TravelPlanScheduleChangeType changeType) {
        return new TravelPlanScheduleChangedEvent(travelPlanId, List.of(dayId), changeType);
    }

    /** 다른 DAY 로 옮겨 두 DAY 가 함께 바뀐 경우. */
    public static TravelPlanScheduleChangedEvent ofMove(
            Long travelPlanId, Long sourceDayId, Long targetDayId) {
        return new TravelPlanScheduleChangedEvent(travelPlanId,
                List.of(sourceDayId, targetDayId), TravelPlanScheduleChangeType.ITEM_MOVED);
    }
}
