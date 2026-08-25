package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
    /** SHA-256(rawToken) 의 hex 64자. 링크 검증은 언제나 이 값으로만 한다. */
    private String tokenHash;
    /**
     * AES-256/GCM 으로 감싼 raw token. 살아 있는 링크를 OWNER 에게 다시 보여 줄 때만 푼다.
     * 링크가 끊기면(REPLACED / DISABLED) NULL 이 되고, 예전 방식으로 만든 행도 NULL 이다.
     * 로그에 딸려 나가지 않도록 toString 에서 뺀다.
     */
    @ToString.Exclude
    private String tokenEncrypted;
    private TravelPlanInvitationStatus status;
    /** REPLACED / DISABLED 로 바뀐 시각. ACTIVE 인 동안에는 NULL. */
    private Timestamp invalidatedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
