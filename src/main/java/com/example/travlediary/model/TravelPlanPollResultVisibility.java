package com.example.travlediary.model;

/**
 * travel_plan_polls.result_visibility.
 * 결과를 언제부터 볼 수 있는지.
 *
 * <p>이번 단계의 생성 화면에서는 고르게 하지 않고 REALTIME 으로만 저장한다.
 * 결과 표시 자체가 다음 단계라 지금은 저장만 해 둔다.
 */
public enum TravelPlanPollResultVisibility {
    /** 진행 중에도 바로 보인다 */
    REALTIME,
    /** 마감된 뒤에 보인다 */
    AFTER_CLOSE
}
