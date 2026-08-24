package com.example.travlediary.service.travelplan;

/**
 * 방 전용 표시 이름 규칙.
 * 방을 만드는 OWNER 와 초대로 들어오는 MEMBER 가 같은 규칙을 쓴다.
 * 사이트 전체의 username / nickname 과는 별개다.
 */
final class TravelPlanDisplayName {

    /** travel_plan_members.display_name 은 varchar(50) */
    static final int MAX_LENGTH = 50;

    private TravelPlanDisplayName() {
    }

    /**
     * 양끝 공백을 정리하고 길이를 확인한다.
     *
     * @return 저장할 표시 이름
     * @throws TravelPlanValidationException 비어 있거나 너무 길 때
     */
    static String normalize(String value) {
        String displayName = value == null ? "" : value.trim();
        if (displayName.isEmpty()) {
            throw new TravelPlanValidationException("displayName",
                    "이 방에서 사용할 표시 이름을 입력해 주세요.");
        }
        if (displayName.length() > MAX_LENGTH) {
            throw new TravelPlanValidationException("displayName",
                    "표시 이름은(는) " + MAX_LENGTH + "자 이하로 입력해 주세요.");
        }
        return displayName;
    }
}
