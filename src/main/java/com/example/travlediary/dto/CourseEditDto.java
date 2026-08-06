package com.example.travlediary.dto;

import lombok.Data;

import java.util.List;

@Data
public class CourseEditDto {
    private Long id;
    private String title;
    private String content;
    private List<CourseStopDto> stops;
}
