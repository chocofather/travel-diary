package com.example.travlediary.model;

/**
 * travel_plan_invitations.status. DB 는 varchar 이고 enum 이름을 그대로 저장한다.
 * 방마다 ACTIVE 는 최대 1건이고, 재발급하면 이전 링크가 REPLACED,
 * OWNER 가 직접 끄면 DISABLED 가 된다. 둘 다 다시 살아나지 않는다.
 */
public enum TravelPlanInvitationStatus {
    ACTIVE, REPLACED, DISABLED
}
