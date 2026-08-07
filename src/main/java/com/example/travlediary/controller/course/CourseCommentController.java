package com.example.travlediary.controller.course;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.dto.CourseCommentRequest;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.course.CourseCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/course-comments")
public class CourseCommentController {

    private final CourseCommentService courseCommentService;

    @GetMapping
    public List<CourseCommentDto> getComments(
            @RequestParam Long courseId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long currentUserId = userDetails == null ? null : userDetails.getId();
        return courseCommentService.getComments(courseId, currentUserId);
    }

    @GetMapping("/page")
    public PageResult<CourseCommentDto> getCommentsPage(
            @RequestParam Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long currentUserId = userDetails == null ? null : userDetails.getId();
        return courseCommentService.getCommentsPage(courseId, currentUserId, page, size, sort);
    }

    @PostMapping
    public ResponseEntity<CourseCommentDto> create(
            @RequestBody CourseCommentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CourseCommentDto created = courseCommentService.create(
                request.getCourseId(), userDetails.getId(), request.getContent(), request.getReplyToCommentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{commentId}")
    public CourseCommentDto update(
            @PathVariable Long commentId,
            @RequestBody CourseCommentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return courseCommentService.update(commentId, userDetails.getId(), request.getContent());
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        courseCommentService.delete(commentId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{commentId}/likes")
    public ResponseEntity<Void> likeComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        courseCommentService.likeComment(commentId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{commentId}/likes")
    public ResponseEntity<Void> unlikeComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        courseCommentService.unlikeComment(commentId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
