package com.example.travlediary.dto;

import lombok.Data;

@Data
public class SeasonDestinationDto {
    private Long id;
    private String name;
    private String imageUrl;
    private String regionName;
    private String season;

    // 태그/카테고리 정보
    private Long categoryId;
    private String categoryName;


}
