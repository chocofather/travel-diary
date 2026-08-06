package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseDetailDto;
import com.example.travlediary.dto.CourseCreateRequest;

public interface CourseService {

    CourseDetailDto getCourseDetail(Long courseId);

    Long createCourse(CourseCreateRequest request, Long userId);

}
