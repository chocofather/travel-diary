package com.example.travlediary.dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class CourseCommentDto {
    private Long id;
    private Long courseId;
    private Long parentCommentId;
    private Long replyToCommentId;
    private String replyToNickname;
    private boolean replyToDeleted;
    private String content;
    private String writerNickname;
    private Long writerUserId;
    private String writerProfileImage;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private boolean deleted;
    private long likeCount;
    private boolean likedByMe;
    private boolean myComment;
}
