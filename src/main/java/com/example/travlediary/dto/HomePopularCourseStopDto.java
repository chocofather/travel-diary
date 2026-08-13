package com.example.travlediary.dto;

import lombok.Data;

@Data
public class HomePopularCourseStopDto {

    private Long courseId;
    private Integer visitOrder;
    private String destinationName;
}
