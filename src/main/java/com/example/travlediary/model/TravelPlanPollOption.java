package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/** travel_plan_poll_options 한 행. 보여 줄 순서는 display_order 로 남는다(1 부터). */
@Data
@NoArgsConstructor
public class TravelPlanPollOption {
    private Long id;
    private Long pollId;
    private String content;
    /** 1 부터. DB CHECK 가 1 미만을 막는다. */
    private Integer displayOrder;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
