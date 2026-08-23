package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDate;

/** travel_plans 한 행. 공동 여행계획 방. */
@Data
@NoArgsConstructor
public class TravelPlan {
    private Long id;
    private Long createdByUserId;          // users.id (방을 만든 사람)
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String representativeImageUrl; // 대표 이미지. 아직 업로드 기능이 없어 NULL 이다
    private TravelPlanStatus status;
    private Timestamp lastActivityAt;
    private Timestamp finalizedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
