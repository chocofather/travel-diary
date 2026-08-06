package com.example.travlediary.dto;

import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
public class CourseDetailDto {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String nickname;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Integer views;
    private List<CourseStopDto> stops;
    private boolean myCourse;
}
