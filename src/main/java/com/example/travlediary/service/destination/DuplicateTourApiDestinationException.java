package com.example.travlediary.service.destination;

/** 같은 TourAPI contentId 의 여행지가 이미 있을 때. 저장 직전 2차 중복 검사와 DB 유니크 제약이 함께 쓴다. */
public class DuplicateTourApiDestinationException extends RuntimeException {

    private final String externalContentId;

    public DuplicateTourApiDestinationException(String externalContentId) {
        super("이미 등록된 TourAPI 여행지입니다. (contentId=" + externalContentId + ")");
        this.externalContentId = externalContentId;
    }

    public String getExternalContentId() {
        return externalContentId;
    }
}
