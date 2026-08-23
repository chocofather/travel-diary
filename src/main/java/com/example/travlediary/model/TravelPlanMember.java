package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/** travel_plan_members 한 행. 방 안에서의 참여자. */
@Data
@NoArgsConstructor
public class TravelPlanMember {
    private Long id;
    private Long travelPlanId;
    private Long userId;
    private String displayName;   // 이 방에서만 쓰는 표시 이름
    private TravelPlanRole role;
    private TravelPlanMemberStatus status;
    private Boolean rejoinAllowed;
    private Timestamp joinedAt;
    private Timestamp leftAt;
    private Timestamp removedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
