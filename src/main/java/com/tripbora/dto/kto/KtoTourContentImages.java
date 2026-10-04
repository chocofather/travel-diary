package com.tripbora.dto.kto;

import java.util.List;

/**
 * 관리자 관광사진 검색에서 쓰는 TourAPI 콘텐츠 한 건과 그 이미지 후보.
 * 이미지의 저작권 구분 코드는 그대로 두고, 허용 여부 판정은 검색 쪽에서 한다.
 */
public record KtoTourContentImages(
        String contentId,
        String title,
        List<KtoTourImageCandidate> images
) {
}
