package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * travel_plan_final_snapshots 한 행. 완료된 여행 계획의 머리다.
 *
 * <p>(travel_plan_id) 가 UNIQUE 라 한 방에 최종본은 하나뿐이다.
 * 두 번 만들려 하면 DB 가 막는다.
 */
@Data
@NoArgsConstructor
public class TravelPlanFinalSnapshot {
    private Long id;
    private Long travelPlanId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String representativeImageUrl;
    private Timestamp finalizedAt;
    private Timestamp createdAt;
}
