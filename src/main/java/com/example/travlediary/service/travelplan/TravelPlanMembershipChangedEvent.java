package com.example.travlediary.service.travelplan;

/**
 * 방의 참여자 명단이 바뀌었다는 서버 내부 알림.
 *
 * <p>누가 어떻게 바뀌었는지는 화면에 알리지 않는다.
 * 화면은 이 알림을 "명단을 다시 읽어라" 는 신호로만 쓰고
 * 사람 수는 서버에서 다시 받아 온다.
 * 그래서 같은 알림을 두 번 받아도 숫자가 두 번 늘지 않는다.
 *
 * <p>커밋이 끝난 뒤에만 다뤄진다.
 * 되돌아간 변경을 다른 화면이 먼저 보는 일이 없어야 한다.
 *
 * <p>초대 참여 / 재참여 / 나가기 / 내보내기 / 방장 넘기기가 모두 이 알림 하나를 쓴다.
 * 재참여 허용은 ACTIVE 인원이 그대로라 보내지 않는다.
 *
 * @param revokedMemberId 이 변경으로 방에서 빠진 사람. 그 사람이 열어 둔 연결을 끊어야 한다.
 *                        아무도 빠지지 않았으면(참여·재참여·방장 넘기기) null 이다.
 *                        이 값은 서버 안에서만 쓰고 방으로 나가는 알림에는 싣지 않는다.
 */
public record TravelPlanMembershipChangedEvent(Long travelPlanId, Long revokedMemberId) {

    /** 사람이 늘거나 역할만 바뀌었다. 아무도 자격을 잃지 않는다. */
    public static TravelPlanMembershipChangedEvent changed(Long travelPlanId) {
        return new TravelPlanMembershipChangedEvent(travelPlanId, null);
    }

    /** 그 사람이 방에서 빠졌다. 나가기든 내보내기든 뒷정리는 같다. */
    public static TravelPlanMembershipChangedEvent revoked(Long travelPlanId, Long memberId) {
        return new TravelPlanMembershipChangedEvent(travelPlanId, memberId);
    }
}
