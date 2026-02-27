package com.example.travlediary.dto;

import lombok.Data;

@Data
public class DestinationSearchResultDto {
    private Long id; // 여행지 PK (상세 페이지 이동용)
    private String name; // 여행지명
    private String shortDescription; // 간략설명
    private String regionName; // 소속 지역명 (예: 종로구)
    private String parentRegionName; // 상위 지역명 (예: 서울)
    private String thumbnailUrl; // 대표 이미지

    // 검색 확장시, 북마크 여부·댓글 수·후기 등 필요하면 나중에 추가

}