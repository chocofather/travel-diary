package com.example.travlediary.dto;

import lombok.Data;

@Data
public class CourseStopDto {
    private Long courseDestinationId;
    private Long destinationId;
    private Integer visitOrder;
    private String name;
    private String shortDescription;
    private String regionName;
    private String imageUrl;
}
