package com.tripbora.dto;

import lombok.Data;

@Data
public class CourseCommentRequest {
    private Long courseId;
    private Long parentCommentId;
    private Long replyToCommentId;
    private String content;
}
