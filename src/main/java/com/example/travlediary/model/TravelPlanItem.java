package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/** travel_plan_items 한 행. DAY 안의 A 일정(자유 텍스트 한 덩어리). */
@Data
@NoArgsConstructor
public class TravelPlanItem {
    private Long id;
    private Long travelPlanDayId;
    private String content;
    /** 태그 UI 는 아직 없어 NULL 이다. */
    private String tag;
    private Integer displayOrder;
    /** 작성자의 travel_plan_members.id. 멤버가 지워지면 NULL 이 된다. */
    private Long createdByMemberId;
    private Integer version;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
