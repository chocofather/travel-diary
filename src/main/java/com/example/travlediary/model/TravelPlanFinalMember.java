package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * travel_plan_final_members 한 행. 완료 시점에 함께한 사람.
 *
 * <p>원본 참여자 행을 가리키지 않고 그때의 이름·역할을 그대로 옮겨 적는다.
 * 나중에 방에서 무슨 일이 있어도 최종본의 명단은 바뀌지 않는다.
 */
@Data
@NoArgsConstructor
public class TravelPlanFinalMember {
    private Long id;
    private Long snapshotId;
    /** 내 완료된 여행을 찾을 때 쓰는 계정 번호. 탈퇴하면 비워진다. */
    private Long userId;
    private String displayName;
    private TravelPlanRole role;
    private Timestamp hiddenAt;
    private Timestamp createdAt;
}
