package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.dto.CommentLocationDto;
import com.example.travlediary.dto.PageResult;

import java.util.List;
import java.util.Optional;

public interface CourseCommentService {

    List<CourseCommentDto> getComments(Long courseId, Long currentUserId);

    PageResult<CourseCommentDto> getCommentsPage(Long courseId, Long currentUserId,
                                                 int page, int size, String sort);

    Optional<CommentLocationDto> getCommentLocation(Long courseId, Long commentId);

    CourseCommentDto create(Long courseId, Long userId, String content, Long replyToCommentId);

    CourseCommentDto update(Long commentId, Long userId, String content);

    void delete(Long commentId, Long userId);

    void likeComment(Long commentId, Long userId);

    void unlikeComment(Long commentId, Long userId);
}
