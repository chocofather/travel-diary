package com.example.travlediary.service.travelplan;

/**
 * 공동 편집방을 열 수 없을 때 사용자에게 보여 줄 안내.
 *
 * <p>흰 오류 화면 대신 왜 못 여는지를 알려 주기 위한 것이다.
 * 다만 아무 관계 없는 사람에게는 그 방이 있는지조차 알리지 않는다.
 */
public enum TravelPlanAccessNotice {

    /** 완료된 여행이고, 그 여행에 함께했던 사람이다. */
    COMPLETED_PARTICIPANT(
            "여행 계획이 완료되었습니다. 최종 일정은 완료된 여행 목록에서 확인할 수 있습니다."),

    /**
     * 완료된 여행이지만 최종 명단에는 없다.
     * 나갔거나 내보내진 뒤에 완료된 경우다. 자세히 알리지 않는다.
     */
    COMPLETED_PAST("이미 종료된 여행 계획입니다."),

    /**
     * 그 방과 아무 관계가 없다.
     * 방이 있는지, 끝났는지조차 알리지 않는 한 가지 말로만 답한다.
     */
    NO_ACCESS("접근할 수 없는 여행 계획입니다.");

    private final String message;

    TravelPlanAccessNotice(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
