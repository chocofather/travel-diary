package com.example.travlediary.service.course;

import com.example.travlediary.dto.CommentLocationDto;
import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.model.CourseComment;
import com.example.travlediary.model.CourseCommentImage;
import com.example.travlediary.repository.course.CourseCommentImageMapper;
import com.example.travlediary.repository.course.CourseCommentMapper;
import com.example.travlediary.service.comment.CommentImageLimitException;
import com.example.travlediary.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseCommentServiceImpl implements CourseCommentService {

    private static final int MAX_CONTENT_LENGTH = 2_000;
    private static final int DEFAULT_PAGE_SIZE = 5;
    private static final int MAX_PAGE_SIZE = 50;
    /** 댓글 하나에 첨부할 수 있는 사진 수 (DB CHECK 제약과 동일) */
    public static final int MAX_COMMENT_IMAGES = 3;
    /** 코스 댓글 사진 저장 위치. 다른 댓글 사진과 섞지 않는다. */
    private static final String IMAGE_DIRECTORY = "course-comments";

    private final CourseCommentMapper courseCommentMapper;
    private final CourseCommentImageMapper courseCommentImageMapper;
    private final FileUploadService fileUploadService;

    @Value("${custom.upload-path}")
    private String uploadPath;

    @Override
    @Transactional(readOnly = true)
    public List<CourseCommentDto> getComments(Long courseId, Long currentUserId) {
        requireActiveCourse(courseId);
        return attachImageUrls(courseCommentMapper.findByCourseId(courseId, currentUserId));
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

        return new PageResult<>(attachImageUrls(mergeRootThreads(roots, replies)), totalThreads,
                safePage, safeSize, totalCommentCount);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommentLocationDto> getCommentLocation(Long courseId, Long commentId) {
        if (courseId == null || commentId == null) {
            return Optional.empty();
        }
        Long rootId = courseCommentMapper.findActiveRootIdForLocation(courseId, commentId);
        if (rootId == null) {
            return Optional.empty();
        }
        int precedingRootCount = courseCommentMapper.countRootCommentsBefore(courseId, rootId);
        return Optional.of(new CommentLocationDto(
                precedingRootCount / DEFAULT_PAGE_SIZE + 1));
    }

    @Override
    @Transactional
    public CourseCommentDto create(Long courseId, Long userId, String content, Long replyToCommentId,
                                   List<MultipartFile> images) {
        requireActiveCourse(courseId);
        String validatedContent = validateContent(content);
        // 3장을 넘으면 앞쪽만 저장하지 않고 요청 전체를 거부한다.
        List<MultipartFile> uploads = validateImageCount(images);
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

        // 이번 요청에서 저장한 파일만 추적한다. 실패하면 그 파일들만 지운다.
        List<String> savedImageUrls = new ArrayList<>();
        try {
            for (MultipartFile imageFile : uploads) {
                savedImageUrls.add(fileUploadService.saveFile(imageFile, IMAGE_DIRECTORY));
            }
            saveCommentImages(comment.getId(), savedImageUrls);
        } catch (RuntimeException e) {
            deleteStoredFiles(savedImageUrls);
            throw e;
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

    /** 실제로 내용이 있는 파일만 추린 뒤 개수를 검증한다. (빈 파일은 장수에서 제외) */
    private List<MultipartFile> validateImageCount(List<MultipartFile> images) {
        List<MultipartFile> uploads = (images == null ? List.<MultipartFile>of() : images).stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (uploads.size() > MAX_COMMENT_IMAGES) {
            throw new CommentImageLimitException(
                    "사진은 최대 " + MAX_COMMENT_IMAGES + "장까지 첨부할 수 있습니다.");
        }
        return uploads;
    }

    /** 첨부 사진을 display_order 1,2,3 순서로 course_comment_images 에만 저장한다. */
    private void saveCommentImages(Long commentId, List<String> imageUrls) {
        for (int i = 0; i < imageUrls.size(); i++) {
            CourseCommentImage image = new CourseCommentImage();
            image.setCommentId(commentId);
            image.setImageUrl(imageUrls.get(i));
            image.setDisplayOrder(i + 1);
            if (courseCommentImageMapper.insert(image) != 1) {
                throw new IllegalStateException("댓글 이미지를 저장하지 못했습니다.");
            }
        }
    }

    /** 저장에 실패했을 때 이번 요청에서 올라간 파일만 정리한다. */
    private void deleteStoredFiles(List<String> imageUrls) {
        for (String imageUrl : imageUrls) {
            if (imageUrl == null || imageUrl.isEmpty()) continue;
            try {
                String relativePath = imageUrl.replaceFirst("^/uploads/", "");
                Files.deleteIfExists(Paths.get(uploadPath, relativePath));
            } catch (IOException ignored) {
                // 파일 정리 실패는 등록 실패 원인을 덮지 않도록 무시한다.
            }
        }
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
        attachImageUrls(List.of(dto));
        return dto;
    }

    /**
     * 댓글 목록의 첨부 사진을 한 번에 읽어 각 DTO 에 채운다. (댓글마다 SELECT 하지 않는다)
     * 사용자 삭제·관리자 조치로 숨겨진 댓글은 조회 대상에서 빼고 빈 목록으로 남겨
     * 플레이스홀더만 노출되게 한다. (사진 행 자체는 지우지 않으므로 복구하면 다시 보인다)
     */
    private List<CourseCommentDto> attachImageUrls(List<CourseCommentDto> comments) {
        List<Long> visibleCommentIds = comments.stream()
                .filter(comment -> !comment.isDeleted())
                .map(CourseCommentDto::getId)
                .toList();
        if (visibleCommentIds.isEmpty()) {
            return comments;
        }

        // XML 에서 comment_id, display_order 순으로 정렬하므로 그룹 안의 순서가 그대로 유지된다.
        Map<Long, List<String>> imagesByComment = courseCommentImageMapper
                .findByCommentIds(visibleCommentIds).stream()
                .collect(Collectors.groupingBy(
                        CourseCommentImage::getCommentId,
                        LinkedHashMap::new,
                        Collectors.mapping(CourseCommentImage::getImageUrl, Collectors.toList())));

        comments.forEach(comment -> comment.setImageUrls(
                imagesByComment.getOrDefault(comment.getId(), List.of())));
        return comments;
    }
}
