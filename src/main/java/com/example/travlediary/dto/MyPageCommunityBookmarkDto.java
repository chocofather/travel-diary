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
    /** 최종 탈퇴(DEACTIVATED) 회원이면 true. 화면은 nickname 대신 공통 문구를 쓴다. */
    private boolean writerWithdrawn;
    private LocalDateTime createdAt;
    private Long views;
    private LocalDateTime bookmarkCreatedAt;
}
