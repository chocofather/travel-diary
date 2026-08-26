package com.example.travlediary.model;

/**
 * travel_plan_polls.close_type.
 * 무엇으로 투표가 마감되는지. 만들 때 정하고 그 뒤로 바뀌지 않는다.
 */
public enum TravelPlanPollCloseType {
    /** 만든 사람이 직접 마감한다 */
    MANUAL,
    /** 지금 방에 있는 사람이 모두 투표하면 그때 마감된다 */
    ALL_VOTED,
    /** 정해진 시각이 지나면 마감된다(deadline_at) */
    DEADLINE
}
