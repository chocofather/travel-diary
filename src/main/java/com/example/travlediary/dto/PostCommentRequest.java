package com.example.travlediary.dto;

import lombok.Data;

@Data
public class PostCommentRequest {
    private Long postId;
    private Long parentCommentId;
    private Long replyToCommentId;
    private String content;
}
