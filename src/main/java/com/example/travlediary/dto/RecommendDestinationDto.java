package com.example.travlediary.dto;

import lombok.Data;

@Data
public class RecommendDestinationDto {
    private Long id;            // 여행지 id
    private String name;        // 여행지명 (destination_translations.name)
    private String imageUrl;    // 대표 이미지 (destination_images)
    private String regionName;  // 지역명 (country_categories.region_name)
    private Long categoryId;    // 카테고리 id
    private int views;          // 조회수 (destinations.views)
    private int bookmarkCount;  // 북마크 수
}
