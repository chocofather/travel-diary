package com.example.travlediary.service.travelplan;

/**
 * 방의 참여자 명단이 바뀌었다는 서버 내부 알림.
 *
 * <p>누가 어떻게 바뀌었는지는 담지 않는다.
 * 화면은 이 알림을 "명단을 다시 읽어라" 는 신호로만 쓰고
 * 사람 수는 서버에서 다시 받아 온다.
 * 그래서 같은 알림을 두 번 받아도 숫자가 두 번 늘지 않는다.
 *
 * <p>커밋이 끝난 뒤에만 다뤄진다.
 * 되돌아간 변경을 다른 화면이 먼저 보는 일이 없어야 한다.
 *
 * <p>초대 참여 / 재참여 / 나가기 / 내보내기 / 방장 넘기기가 모두 이 알림 하나를 쓴다.
 * 재참여 허용은 ACTIVE 인원이 그대로라 보내지 않는다.
 */
public record TravelPlanMembershipChangedEvent(Long travelPlanId) {
}
