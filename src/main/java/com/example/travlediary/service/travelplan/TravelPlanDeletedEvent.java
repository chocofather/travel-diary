package com.example.travlediary.service.travelplan;

/**
 * 진행 중이던 공동 여행계획이 실제로 사라졌다는 서버 내부 알림.
 *
 * <p>커밋이 끝난 뒤에만 다뤄진다. 되돌아간 삭제를 보고 방을 나가면
 * 아직 살아 있는 방에서 사람들이 튕겨 나간다.
 *
 * <p>방 자체가 없어졌으므로 이 알림이 실린 뒤에는 그 방으로 아무것도 보내지 않는다.
 * 사람마다 따로 내보내지 않고, 방을 보고 있는 모두가 이 알림 하나를 함께 받는다.
 */
public record TravelPlanDeletedEvent(Long travelPlanId) {
}
