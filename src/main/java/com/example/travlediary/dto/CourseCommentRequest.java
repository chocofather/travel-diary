package com.example.travlediary.dto;

import lombok.Data;

@Data
public class CourseCommentRequest {
    private Long courseId;
    private String content;
}
