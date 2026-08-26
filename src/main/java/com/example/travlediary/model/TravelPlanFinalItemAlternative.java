package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * travel_plan_final_item_alternatives 한 행. 완료된 계획의 대안(B/C).
 * DB CHECK 가 1(B) 과 2(C) 만 받는다.
 */
@Data
@NoArgsConstructor
public class TravelPlanFinalItemAlternative {
    private Long id;
    private Long finalItemId;
    private Integer alternativeOrder;
    private String conditionLabel;
    private String content;
    private String tag;
    private Timestamp createdAt;
}
