package com.example.travlediary.model;

/**
 * travel_plan_polls.status.
 * 컬럼 DEFAULT 가 'OPEN' 이라 만들어진 투표는 곧바로 진행 중이다.
 * 마감(CLOSED)은 다음 단계에서 다룬다.
 */
public enum TravelPlanPollStatus {
    OPEN,
    CLOSED
}
