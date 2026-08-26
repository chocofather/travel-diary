package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanPollEventDto;

/**
 * 투표가 실제로 저장됐다는 서버 내부 알림.
 *
 * <p>일정·채팅과 같은 방식이다. Service 는 이 이벤트만 발행하고
 * 실제 전송은 커밋이 끝난 뒤 리스너가 맡는다.
 * 선택지 저장이 하나라도 실패해 롤백되면 아무것도 나가지 않는다.
 */
public record TravelPlanPollChangedEvent(Long travelPlanId, TravelPlanPollEventDto payload) {
}
