package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.model.CourseComment;
import com.example.travlediary.repository.course.CourseCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CourseCommentServiceImpl implements CourseCommentService {

    private static final int MAX_CONTENT_LENGTH = 2_000;
    private static final int DEFAULT_PAGE_SIZE = 5;
    private static final int MAX_PAGE_SIZE = 50;

    private final CourseCommentMapper courseCommentMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CourseCommentDto> getComments(Long courseId, Long currentUserId) {
        requireActiveCourse(courseId);
        return courseCommentMapper.findByCourseId(courseId, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CourseCommentDto> getCommentsPage(Long courseId, Long currentUserId,
                                                        int page, int size, String sort) {
        requireActiveCourse(courseId);
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        String safeSort = normalizeSort(sort);
        int totalThreads = courseCommentMapper.countRootCommentThreads(courseId);
        int totalCommentCount = courseCommentMapper.countActiveComments(courseId);
        long offset = (long) safePage * safeSize;

        if (totalThreads == 0 || offset >= totalThreads) {
            return new PageResult<>(List.of(), totalThreads, safePage, safeSize, totalCommentCount);
        }

        List<CourseCommentDto> roots = courseCommentMapper.findPagedRootComments(
                courseId, currentUserId, safeSort, safeSize, (int) offset);
        List<Long> rootIds = roots.stream().map(CourseCommentDto::getId).toList();
        List<CourseCommentDto> replies = rootIds.isEmpty()
                ? List.of()
                : courseCommentMapper.findRepliesForRootComments(courseId, currentUserId, rootIds);

        return new PageResult<>(mergeRootThreads(roots, replies), totalThreads,
                safePage, safeSize, totalCommentCount);
    }

    @Override
    @Transactional
    public CourseCommentDto create(Long courseId, Long userId, String content, Long replyToCommentId) {
        requireActiveCourse(courseId);
        String validatedContent = validateContent(content);
        Long parentCommentId = resolveParentCommentId(courseId, replyToCommentId);

        CourseComment comment = new CourseComment();
        comment.setCourseId(courseId);
        comment.setUserId(userId);
        comment.setContent(validatedContent);
        comment.setParentCommentId(parentCommentId);
        comment.setReplyToCommentId(replyToCommentId);

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

    @Override
    @Transactional
    public void likeComment(Long commentId, Long userId) {
        requireLockedActiveComment(commentId);
        courseCommentMapper.insertLike(userId, commentId);
    }

    @Override
    @Transactional
    public void unlikeComment(Long commentId, Long userId) {
        requireLockedActiveComment(commentId);
        courseCommentMapper.deleteLike(userId, commentId);
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

    private CourseComment requireLockedActiveComment(Long commentId) {
        CourseComment comment = commentId == null
                ? null
                : courseCommentMapper.findActiveCommentForUpdate(commentId);
        if (comment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        return comment;
    }

    private Long resolveParentCommentId(Long courseId, Long replyToCommentId) {
        if (replyToCommentId == null) {
            return null;
        }

        CourseComment target = courseCommentMapper.findActiveCommentForUpdate(replyToCommentId);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "답글 대상 댓글을 찾을 수 없습니다.");
        }
        if (!Objects.equals(target.getCourseId(), courseId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "같은 여행 코스의 댓글에만 답글을 작성할 수 있습니다.");
        }

        if (target.getParentCommentId() == null) {
            return target.getId();
        }

        CourseComment root = courseCommentMapper.findCommentForUpdate(target.getParentCommentId());
        if (root == null || root.getParentCommentId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "원댓글 구조가 올바르지 않습니다.");
        }
        if (!Objects.equals(root.getCourseId(), courseId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 여행 코스의 원댓글만 사용할 수 있습니다.");
        }
        return root.getId();
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

    private String normalizeSort(String sort) {
        return switch (sort == null ? "" : sort) {
            case "oldest" -> "oldest";
            case "likes" -> "likes";
            default -> "latest";
        };
    }

    private List<CourseCommentDto> mergeRootThreads(List<CourseCommentDto> roots,
                                                    List<CourseCommentDto> replies) {
        Map<Long, List<CourseCommentDto>> repliesByRoot = new HashMap<>();
        for (CourseCommentDto reply : replies) {
            repliesByRoot.computeIfAbsent(reply.getParentCommentId(), ignored -> new ArrayList<>())
                    .add(reply);
        }

        List<CourseCommentDto> merged = new ArrayList<>(roots.size() + replies.size());
        for (CourseCommentDto root : roots) {
            merged.add(root);
            merged.addAll(repliesByRoot.getOrDefault(root.getId(), List.of()));
        }
        return merged;
    }

    private CourseCommentDto requireLatestDto(Long commentId, Long currentUserId) {
        CourseCommentDto dto = courseCommentMapper.findDtoById(commentId, currentUserId);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        return dto;
    }
}
