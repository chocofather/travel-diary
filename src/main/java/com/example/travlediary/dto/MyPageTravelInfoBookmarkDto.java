package com.example.travlediary.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MyPageTravelInfoBookmarkDto {
    private Long targetId;
    private String title;
    private String scope;
    private String contentType;
    private Long categoryId; // 카테고리 이름을 언어별로 바꿀 때 쓴다
    private String categoryName;
    private String thumbnailUrl;
    private LocalDateTime createdAt;
    private LocalDateTime bookmarkCreatedAt;
}
