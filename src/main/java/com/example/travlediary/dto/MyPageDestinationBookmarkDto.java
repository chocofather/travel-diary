package com.example.travlediary.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MyPageDestinationBookmarkDto {
    private Long targetId;
    private String name;
    private String regionName;
    private String thumbnailUrl;
    private LocalDateTime bookmarkCreatedAt;
}
