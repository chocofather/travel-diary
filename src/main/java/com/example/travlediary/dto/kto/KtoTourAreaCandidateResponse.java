package com.example.travlediary.dto.kto;

/**
 * 지역별 일괄 가져오기 후보 한 건.
 * {@code registered} 는 TourAPI contentId 기준 1차 중복 검사 결과다 (여행지명으로 판정하지 않는다).
 */
public record KtoTourAreaCandidateResponse(
        String contentId,
        String contentTypeId,
        String contentTypeName,
        String title,
        String address,
        String thumbnailUrl,
        boolean registered
) {
    public KtoTourAreaCandidateResponse withRegistered(boolean registeredNow) {
        return new KtoTourAreaCandidateResponse(
                contentId, contentTypeId, contentTypeName, title, address, thumbnailUrl,
                registeredNow);
    }
}
