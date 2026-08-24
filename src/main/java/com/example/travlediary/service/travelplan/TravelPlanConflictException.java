package com.example.travlediary.service.travelplan;

/**
 * 낙관적 잠금 충돌.
 * 내가 화면에 들고 있던 version 사이에 다른 참여자의 변경이 먼저 반영된 경우다.
 */
public class TravelPlanConflictException extends RuntimeException {

    public TravelPlanConflictException() {
        super("다른 변경이 먼저 반영되었습니다. 새로고침 후 다시 시도해 주세요.");
    }
}
