package com.tripbora.dto.kto;

import com.tripbora.service.destination.DestinationDuplicateCheck;
import com.tripbora.service.destination.DestinationDuplicateStatus;

/**
 * 지역별 일괄 가져오기 후보 한 건.
 * {@code duplicate} 는 공통 중복 판별 결과이고, {@code registered} 는 그중 확정 중복(같은 contentId 등)일 때만 true 다.
 * 이름·위치만 비슷한 후보는 registered 가 아니라 duplicate.status = POSSIBLE_DUPLICATE 로 표시한다.
 *
 * @param longitude TourAPI mapx. 중복 판별(좌표 근접)에 쓴다
 * @param latitude  TourAPI mapy
 */
public record KtoTourAreaCandidateResponse(
        String contentId,
        String contentTypeId,
        String contentTypeName,
        String title,
        String address,
        String thumbnailUrl,
        String longitude,
        String latitude,
        boolean registered,
        DestinationDuplicateCheck duplicate
) {
    public KtoTourAreaCandidateResponse(String contentId, String contentTypeId, String contentTypeName,
                                        String title, String address, String thumbnailUrl, boolean registered) {
        this(contentId, contentTypeId, contentTypeName, title, address, thumbnailUrl, null, null,
                registered, null);
    }

    public KtoTourAreaCandidateResponse withDuplicate(DestinationDuplicateCheck check) {
        DestinationDuplicateCheck safe = check == null ? DestinationDuplicateCheck.NOT_REGISTERED : check;
        return new KtoTourAreaCandidateResponse(
                contentId, contentTypeId, contentTypeName, title, address, thumbnailUrl, longitude, latitude,
                safe.confirmed(), safe);
    }

    /** 판별 전이면 registered 값으로만 본다. */
    public DestinationDuplicateStatus duplicateStatus() {
        if (duplicate != null) {
            return duplicate.status();
        }
        return registered ? DestinationDuplicateStatus.REGISTERED : DestinationDuplicateStatus.NOT_REGISTERED;
    }
}
