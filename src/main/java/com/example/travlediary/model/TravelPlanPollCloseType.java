package com.example.travlediary.model;

/**
 * travel_plan_polls.close_type.
 * 무엇으로 투표가 마감되는지.
 *
 * <p>이번 단계의 생성 화면에서는 고르게 하지 않고 MANUAL 로만 저장한다.
 * 마감 기능 자체가 다음 단계라 deadline_at 도 비워 둔다.
 */
public enum TravelPlanPollCloseType {
    /** 만든 사람이 직접 마감한다 */
    MANUAL,
    /** 정해진 시각에 마감된다(deadline_at) */
    DEADLINE
}
