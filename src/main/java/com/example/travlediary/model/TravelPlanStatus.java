package com.example.travlediary.model;

/**
 * travel_plans.status. DB 는 varchar 이고 enum 이름을 그대로 저장한다.
 *
 * <p>완료는 두 걸음으로 간다. 최종본을 뜨는 동안 FINALIZING 으로 두어
 * 그 사이에 들어오는 일정 저장이 ACTIVE 조건에서 걸리게 한다.
 */
public enum TravelPlanStatus {
    /** 함께 계획을 짜는 중 */
    ACTIVE,
    /** 최종본을 뜨는 중. 짧게 지나가는 상태이고 이때도 일정을 고칠 수 없다 */
    FINALIZING,
    /** 완료됐다. 최종본이 남아 있고 원본 일정은 더 이상 바뀌지 않는다 */
    COMPLETED
}
