package com.example.travlediary.service.travelplan;

/**
 * 여행 계획이 실제로 완료됐다는 서버 내부 알림.
 *
 * <p>커밋이 끝난 뒤에만 다뤄진다.
 * 그래야 되돌아간 완료 때문에 남의 작성 중 내용이 사라지는 일이 없다.
 */
public record TravelPlanCompletedEvent(Long travelPlanId) {
}
