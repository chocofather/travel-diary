package com.example.travlediary.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MyPageCommunityBookmarkDto {
    private Long targetId;
    private String boardType;
    private String postType;
    private String title;
    private String nickname;
    private LocalDateTime createdAt;
    private Long views;
    private LocalDateTime bookmarkCreatedAt;
}
