package com.example.travlediary.model;

/**
 * travel_plan_members.status. DB 는 varchar 이고 enum 이름을 그대로 저장한다.
 *
 * <p>ACTIVE 만 방의 참여자로 센다.
 * LEFT(스스로 나감) / REMOVED(내보내짐) 는 아직 그 기능을 만들지 않았지만,
 * 그런 row 가 남아 있는 사람이 초대 링크로 새 참여자가 되어 버리지 않도록 여기서 읽을 수 있어야 한다.
 * 나가기 / 강퇴 / 재참여 자체는 해당 기능을 구현하는 단계에서 다룬다.
 */
public enum TravelPlanMemberStatus {
    ACTIVE, LEFT, REMOVED
}
