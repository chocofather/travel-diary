package com.example.travlediary.dto;

import lombok.Data;

@Data
public class SeasonDestinationDto {
    private Long id;
    private String name;
    private String imageUrl;
    private Long regionId;
    private String regionName;
    private String season;

    // 태그/카테고리 정보
    private Long categoryId;
    private String categoryName;

    // 카드 썸네일 (여행지 업로드 사진이 아니면 null → imageUrl 을 쓴다), 후보 srcset (폭은 실제 픽셀)
    private String cardImageUrl;
    private String cardImageSrcset;


}
