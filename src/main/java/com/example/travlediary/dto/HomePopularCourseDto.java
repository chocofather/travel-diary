package com.example.travlediary.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HomePopularCourseDto {

    private Long courseId;
    private String title;
    private String nickname;
    private int views;
    private int totalDestinationCount;
    private List<String> previewDestinationNames = new ArrayList<>();

    public String getDetailUrl() {
        return "/course/" + courseId;
    }

    public String getRoutePreview() {
        return String.join(" → ", previewDestinationNames);
    }

    public int getRemainingDestinationCount() {
        return Math.max(totalDestinationCount - previewDestinationNames.size(), 0);
    }
}
