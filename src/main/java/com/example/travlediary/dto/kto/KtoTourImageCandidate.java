package com.example.travlediary.dto.kto;

/**
 * TourAPI 가 주는 저장 후보 이미지 한 장.
 * 대표이미지(detailCommon2 firstimage)는 {@code main} 이 true 이고,
 * 추가이미지(detailImage2 originimgurl)는 false 다.
 */
public record KtoTourImageCandidate(
        String contentId,
        String imageName,
        String imageUrl,
        String copyrightDivisionCode,
        boolean main
) {
}
