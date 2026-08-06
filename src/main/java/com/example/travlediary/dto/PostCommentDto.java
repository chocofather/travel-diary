package com.example.travlediary.dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class PostCommentDto {
    private Long id;
    private Long postId;
    private String content;
    private String writerNickname;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private boolean myComment;
}
