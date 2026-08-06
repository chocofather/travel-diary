package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.model.CourseComment;
import com.example.travlediary.repository.course.CourseCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CourseCommentServiceImpl implements CourseCommentService {

    private static final int MAX_CONTENT_LENGTH = 2_000;

    private final CourseCommentMapper courseCommentMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CourseCommentDto> getComments(Long courseId, Long currentUserId) {
        requireActiveCourse(courseId);
        return courseCommentMapper.findByCourseId(courseId, currentUserId);
    }

    @Override
    @Transactional
    public CourseCommentDto create(Long courseId, Long userId, String content) {
        requireActiveCourse(courseId);
        String validatedContent = validateContent(content);

        CourseComment comment = new CourseComment();
        comment.setCourseId(courseId);
        comment.setUserId(userId);
        comment.setContent(validatedContent);
        comment.setParentCommentId(null);

        if (courseCommentMapper.insert(comment) != 1 || comment.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "댓글 등록에 실패했습니다.");
        }
        return requireLatestDto(comment.getId(), userId);
    }

    @Override
    @Transactional
    public CourseCommentDto update(Long commentId, Long userId, String content) {
        CourseComment comment = requireOwnedActiveComment(commentId, userId);
        String validatedContent = validateContent(content);

        if (courseCommentMapper.updateContent(commentId, userId, validatedContent) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        return requireLatestDto(comment.getId(), userId);
    }

    @Override
    @Transactional
    public void delete(Long commentId, Long userId) {
        requireOwnedActiveComment(commentId, userId);
        if (courseCommentMapper.softDelete(commentId, userId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
    }

    private void requireActiveCourse(Long courseId) {
        if (courseId == null || !courseCommentMapper.existsActiveCourse(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "여행 코스를 찾을 수 없습니다.");
        }
    }

    private CourseComment requireOwnedActiveComment(Long commentId, Long userId) {
        CourseComment comment = commentId == null ? null : courseCommentMapper.findActiveComment(commentId);
        if (comment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        if (!Objects.equals(comment.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "댓글 변경 권한이 없습니다.");
        }
        return comment;
    }

    private String validateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해 주세요.");
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글은 2,000자 이하로 입력해 주세요.");
        }
        return trimmed;
    }

    private CourseCommentDto requireLatestDto(Long commentId, Long currentUserId) {
        CourseCommentDto dto = courseCommentMapper.findDtoById(commentId, currentUserId);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        return dto;
    }
}
