package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanChatEventDto;

/**
 * 채팅이 실제로 저장됐다는 서버 내부 알림.
 *
 * <p>일정 변경과 같은 방식이다. Service 는 이 이벤트만 발행하고
 * 실제 전송은 커밋이 끝난 뒤 리스너가 맡는다.
 * 저장에 실패해 롤백되면 아무것도 나가지 않아, 다른 사람 화면에만 남는 메시지가 생기지 않는다.
 */
public record TravelPlanChatChangedEvent(Long travelPlanId, TravelPlanChatEventDto payload) {
}
