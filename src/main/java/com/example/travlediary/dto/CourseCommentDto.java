package com.example.travlediary.dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class CourseCommentDto {
    private Long id;
    private Long courseId;
    private Long parentCommentId;
    private String content;
    private String writerNickname;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private boolean deleted;
    private boolean myComment;
}
