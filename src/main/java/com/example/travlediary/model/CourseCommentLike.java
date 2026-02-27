package com.example.travlediary.model;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class CourseCommentLike {
    private Long userId; // 좋아요 누른 유저
    private Long commentId; // 해당 댓글 아이디
    private Timestamp createdAt; // 좋아요 누른 시간
}
