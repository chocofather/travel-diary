package com.tripbora.service.destination;

/** 중복 판별 근거. 앞의 둘은 확정({@link DestinationDuplicateStatus#REGISTERED}), 뒤의 둘은 확인 필요다. */
public enum DestinationDuplicateReason {
    EXTERNAL_CONTENT_ID("같은 외부 콘텐츠 ID"),
    GOOGLE_PLACE_ID("같은 Google Place ID"),
    NAME_AND_NEARBY("같은 이름 · 가까운 위치"),
    NAME_AND_REGION("같은 이름 · 같은 지역");

    private final String label;

    DestinationDuplicateReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
