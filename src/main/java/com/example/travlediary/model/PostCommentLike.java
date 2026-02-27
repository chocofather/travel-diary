package com.example.travlediary.model;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class PostCommentLike {
    private Long userId;
    private Long commentId;
    private Timestamp createdAt;
}
