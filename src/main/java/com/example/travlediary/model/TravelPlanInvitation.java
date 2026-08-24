package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * travel_plan_invitations 한 행. 방으로 들어오는 초대 링크 1건이다.
 * raw token 은 발급 순간에만 존재하고 DB 에는 SHA-256 hex 만 남는다.
 */
@Data
@NoArgsConstructor
public class TravelPlanInvitation {
    private Long id;
    private Long travelPlanId;
    /** 링크를 발급한 OWNER 의 users.id */
    private Long createdByUserId;
    /** SHA-256(rawToken) 의 hex 64자. raw token 은 저장하지 않는다. */
    private String tokenHash;
    private TravelPlanInvitationStatus status;
    /** REPLACED / DISABLED 로 바뀐 시각. ACTIVE 인 동안에는 NULL. */
    private Timestamp invalidatedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
