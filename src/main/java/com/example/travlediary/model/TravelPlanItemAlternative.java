package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * travel_plan_item_alternatives 한 행.
 * A 일정(travel_plan_items) 하나에 붙는 대안이며, alternative_order 1 이 B, 2 가 C 다.
 * 한 A 일정당 최대 2건까지만 둔다.
 */
@Data
@NoArgsConstructor
public class TravelPlanItemAlternative {
    private Long id;
    /** 붙어 있는 A 일정. 부모가 지워지면 함께 지워진다(CASCADE). */
    private Long travelPlanItemId;
    /** 1 = B, 2 = C */
    private Integer alternativeOrder;
    /** "비가 많이 올 때" 같은 조건. 없으면 NULL. */
    private String conditionLabel;
    private String content;
    /** 태그 UI 는 아직 없어 NULL 이다. */
    private String tag;
    /** 작성자의 travel_plan_members.id. 멤버가 지워지면 NULL 이 된다. */
    private Long createdByMemberId;
    private Integer version;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
