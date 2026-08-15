package com.example.travlediary.controller.destination;

import com.example.travlediary.dto.CommentDto;
import com.example.travlediary.dto.CommentImageDto;
import com.example.travlediary.dto.CommentLocationDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.comment.CommentImageLimitException;
import com.example.travlediary.service.comment.CommentLikeService;
import com.example.travlediary.service.comment.DestinationCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/comments")
public class DestinationCommentController {

    private final CommentLikeService commentLikeService;
    private final DestinationCommentService destinationCommentService;

    // 댓글 좋아요 토글 API
    @PostMapping("/{commentId}/like-toggle")
    public ResponseEntity<String> toggleLike(@PathVariable Long commentId,
                                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인 필요");
        }

        Long userId = userDetails.getId();
        boolean liked = commentLikeService.toggleLike(userId, commentId);
        return ResponseEntity.ok(liked ? "liked" : "unliked");
    }

    @GetMapping("/list")
    public ResponseEntity<List<CommentDto>> getComments(@RequestParam Long destinationId,
                                                        @RequestParam(defaultValue = "oldest") String sort,
                                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = (userDetails != null) ? userDetails.getId() : null;
        List<CommentDto> comments = destinationCommentService.getCommentDtosWithWriter(destinationId, userId, sort);
        return ResponseEntity.ok(comments);
    }

    // 댓글 삭제 (소프트 삭제: deleted = true 처리)
    @DeleteMapping("/{commentId}")
    public ResponseEntity<?> delete(@PathVariable Long commentId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails) {
        // 로그인한 사용자가 해당 댓글의 작성자인 경우만 삭제 허용
        boolean deleted = destinationCommentService.softDelete(commentId, userDetails.getId());

        if (deleted) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("삭제 권한 없음");
        }
    }

    // 댓글 수정: 로그인한 사용자가 본인 댓글만 수정 가능
    @PutMapping("/{commentId}")
    public ResponseEntity<?> update(@PathVariable Long commentId,
                                    @RequestBody Map<String, String> payload,
                                    @AuthenticationPrincipal CustomUserDetails userDetails) {

        String content = payload.get("content"); // 프론트에서 받은 수정된 댓글 내용

        // 서비스 호출: 수정 성공 여부 반환
        boolean result = destinationCommentService.updateComment(commentId, userDetails.getId(), content);

        if (result) {
            return ResponseEntity.ok().build(); // 성공
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("수정 권한 없음 또는 실패");
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestParam("destinationId") Long destinationId,
                                    @RequestParam("content") String content,
                                    @RequestParam(value = "images", required = false) List<MultipartFile> images,
                                    @RequestParam(value = "parentCommentId", required = false) Long parentCommentId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            CommentDto dto = destinationCommentService.create(
                    destinationId, userDetails.getId(), content, images, parentCommentId);
            return ResponseEntity.ok(dto);
        } catch (CommentImageLimitException e) {
            // 프런트에서 그대로 안내할 수 있도록 메시지를 JSON 으로 돌려준다.
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 댓글 이미지 추출
    @GetMapping("/images")
    public ResponseEntity<List<CommentImageDto>> getCommentImages(@RequestParam Long destinationId) {
        List<CommentImageDto> images = destinationCommentService.getCommentImageDtos(destinationId);
        return ResponseEntity.ok(images);
    }

    @GetMapping("/list/page")
    public ResponseEntity<PageResult<CommentDto>> getCommentsPaged(
            @RequestParam Long destinationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = (userDetails != null) ? userDetails.getId() : null;
        PageResult<CommentDto> result = destinationCommentService.getCommentsPaged(destinationId, userId, page, size, sort);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{commentId}/location")
    public ResponseEntity<CommentLocationDto> getCommentLocation(
            @PathVariable Long commentId,
            @RequestParam Long destinationId
    ) {
        return destinationCommentService.getCommentLocation(destinationId, commentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}
