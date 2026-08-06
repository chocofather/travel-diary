package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseDetailDto;
import com.example.travlediary.dto.CourseCreateRequest;
import com.example.travlediary.dto.CourseEditDto;
import com.example.travlediary.dto.CourseUpdateRequest;

public interface CourseService {

    CourseDetailDto getCourseDetail(Long courseId, Long currentUserId);

    CourseEditDto getCourseForEdit(Long courseId, Long userId);

    Long createCourse(CourseCreateRequest request, Long userId);

    void updateCourse(Long courseId, Long userId, CourseUpdateRequest request);

    void deleteCourse(Long courseId, Long userId);

}
