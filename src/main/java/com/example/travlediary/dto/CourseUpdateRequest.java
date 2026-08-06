package com.example.travlediary.dto;

import lombok.Data;

import java.util.List;

@Data
public class CourseUpdateRequest {
    private String title;
    private String content;
    private List<Long> destinationIds;
}
