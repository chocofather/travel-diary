package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCommentDto;

import java.util.List;

public interface CourseCommentService {

    List<CourseCommentDto> getComments(Long courseId, Long currentUserId);

    CourseCommentDto create(Long courseId, Long userId, String content);

    CourseCommentDto update(Long commentId, Long userId, String content);

    void delete(Long commentId, Long userId);
}
