package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class MyPageCommentDto {
    private Long commentId;
    private String commentType;
    private String contentPreview;
    private LocalDateTime createdAt;
    private boolean reply;
    private Long targetId;
    private String targetTitle;
    private String postType;
}
