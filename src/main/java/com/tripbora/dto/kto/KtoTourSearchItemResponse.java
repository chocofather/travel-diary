package com.tripbora.dto.kto;

import com.tripbora.service.destination.DestinationDuplicateCheck;

/**
 * 등록폼 TourAPI 검색 후보 한 건.
 *
 * @param duplicate 이미 등록된 여행지인지 공통 중복 판별 결과. 판별 전이면 null
 */
public record KtoTourSearchItemResponse(
        String contentId,
        String contentTypeId,
        String contentTypeName,
        String title,
        String address,
        String longitude,
        String latitude,
        DestinationDuplicateCheck duplicate
) {
    public KtoTourSearchItemResponse(String contentId, String contentTypeId, String contentTypeName,
                                     String title, String address, String longitude, String latitude) {
        this(contentId, contentTypeId, contentTypeName, title, address, longitude, latitude, null);
    }

    public KtoTourSearchItemResponse withDuplicate(DestinationDuplicateCheck check) {
        return new KtoTourSearchItemResponse(contentId, contentTypeId, contentTypeName, title, address,
                longitude, latitude, check);
    }
}
