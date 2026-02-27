package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class CommentLike {
    private Long userId;         // 좋아요 누른 유저
    private Long commentId;      // 대상 댓글 ID
    private Timestamp createdAt; // 좋아요 누른 시각
}
