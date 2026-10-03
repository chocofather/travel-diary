package com.example.travlediary.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MyPageDestinationBookmarkDto {
    private Long targetId;
    private String name;
    private String regionName;
    private String thumbnailUrl;
    // 대표 이미지가 공공누리 제3유형(변경금지)이면 true → 잘리지 않게(contain) 그린다
    private boolean imageNoDerivatives;
    private LocalDateTime bookmarkCreatedAt;
}
