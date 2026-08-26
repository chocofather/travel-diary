package com.example.travlediary.model;

/**
 * travel_plan_polls.close_reason.
 * 무엇 때문에 실제로 마감됐는지. 마감되기 전에는 비어 있다.
 *
 * <p>정해 둔 방식(close_type)과 대개 같지만 별개의 값이다.
 * 마감은 한 번만 일어나므로 이 값도 한 번만 채워진다.
 */
public enum TravelPlanPollCloseReason {
    /** 만든 사람이 직접 마감했다 */
    MANUAL,
    /** 지금 방에 있는 사람이 모두 투표해서 마감됐다 */
    ALL_VOTED,
    /** 정해진 시각이 지나 마감됐다 */
    DEADLINE
}
