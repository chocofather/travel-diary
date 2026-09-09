package com.example.travlediary.controller.course;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.CommentLocationDto;
import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.dto.CourseCommentRequest;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.comment.CommentImageLimitException;
import com.example.travlediary.service.course.CourseCommentService;
import com.example.travlediary.service.translation.ContentTranslationResponse;
import com.example.travlediary.service.translation.ContentTranslationService;
import com.example.travlediary.service.translation.MachineTranslationException;
import com.example.travlediary.service.translation.TranslatableContentType;
import com.example.travlediary.service.translation.TranslationDailyLimitException;
import com.example.travlediary.service.translation.TranslationMonthlyLimitException;
import com.example.travlediary.service.translation.TranslationNotFoundException;
import com.example.travlediary.service.translation.TranslationRateLimitException;
import com.example.travlediary.service.translation.TranslationStaleException;
import com.example.travlediary.service.translation.TranslationTooLongException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/course-comments")
public class CourseCommentController {

    private final CourseCommentService courseCommentService;
    private final ContentTranslationService contentTranslationService;

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

    @GetMapping("/{commentId}/location")
    public ResponseEntity<CommentLocationDto> getCommentLocation(
            @PathVariable Long commentId,
            @RequestParam Long courseId
    ) {
        return courseCommentService.getCommentLocation(courseId, commentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{commentId}/translation")
    public ResponseEntity<?> translateComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request) {
        String targetLanguage = SupportedLanguage.fromLocale(LocaleContextHolder.getLocale())
                .orElse(SupportedLanguage.KOREAN)
                .getLanguageTag();
        Long userId = userDetails == null ? null : userDetails.getId();
        try {
            ContentTranslationResponse response = contentTranslationService.translate(
                    TranslatableContentType.COURSE_COMMENT,
                    commentId,
                    targetLanguage,
                    request.getRemoteAddr(),
                    userId);
            if ("PROCESSING".equals(response.status())) {
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .header("Retry-After", Long.toString(response.retryAfterSeconds()))
                        .body(response);
            }
            return ResponseEntity.ok(response);
        } catch (TranslationNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (TranslationTooLongException e) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("message", "번역할 수 있는 댓글 길이를 초과했습니다."));
        } catch (TranslationRateLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", Long.toString(e.retryAfterSeconds()))
                    .body(Map.of("message", "번역 요청이 많습니다. 잠시 후 다시 시도해주세요."));
        } catch (TranslationDailyLimitException | TranslationMonthlyLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "현재 번역 요청을 이용할 수 없습니다. 잠시 후 다시 시도해주세요."));
        } catch (TranslationStaleException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "댓글이 변경되었습니다. 다시 시도해주세요."));
        } catch (MachineTranslationException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "지금은 번역을 사용할 수 없습니다."));
        }
    }

    /**
     * 댓글/답글 등록. 사진 첨부를 위해 multipart 로만 받는다.
     * 일반 댓글과 답글 모두 같은 경로에서 최대 3장까지 지원한다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @ModelAttribute CourseCommentRequest request,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            CourseCommentDto created = courseCommentService.create(
                    request.getCourseId(), userDetails.getId(), request.getContent(),
                    request.getReplyToCommentId(), images);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (CommentImageLimitException e) {
            // 프런트에서 그대로 안내할 수 있도록 메시지를 JSON 으로 돌려준다.
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
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
