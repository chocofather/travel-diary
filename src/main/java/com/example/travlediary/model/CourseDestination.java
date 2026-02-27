package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CourseDestination {
    private Long id; // 코스여행지 번호
    private Integer visitOrder; // 방문순서
    private Long courseId; // 코스번호
    private Long destinationId; // 여행지번호
}
