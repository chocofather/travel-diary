package com.example.travlediary.dto;

import lombok.Data;

@Data
public class HomePopularCourseStopDto {

    private Long courseId;
    private Long destinationId;
    private Integer visitOrder;
    private String destinationName;
}
