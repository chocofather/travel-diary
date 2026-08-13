package com.example.travlediary.dto;

import lombok.Data;

import java.util.List;

@Data
public class CourseCreateRequest {
    private Long countryId;
    private String title;
    private String content;
    private List<Long> destinationIds;
}
