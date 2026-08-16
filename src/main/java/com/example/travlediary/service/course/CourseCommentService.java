package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.dto.CommentLocationDto;
import com.example.travlediary.dto.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface CourseCommentService {

    List<CourseCommentDto> getComments(Long courseId, Long currentUserId);

    PageResult<CourseCommentDto> getCommentsPage(Long courseId, Long currentUserId,
                                                 int page, int size, String sort);

    Optional<CommentLocationDto> getCommentLocation(Long courseId, Long commentId);

    /** 댓글/답글 등록. 사진은 최대 3장까지 함께 저장한다. */
    CourseCommentDto create(Long courseId, Long userId, String content, Long replyToCommentId,
                            List<MultipartFile> images);

    CourseCommentDto update(Long commentId, Long userId, String content);

    void delete(Long commentId, Long userId);

    void likeComment(Long commentId, Long userId);

    void unlikeComment(Long commentId, Long userId);
}
