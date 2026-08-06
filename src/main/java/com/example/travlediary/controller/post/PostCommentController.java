package com.example.travlediary.controller.post;

import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.dto.PostCommentRequest;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.post.PostCommentService;
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
@RequestMapping("/post-comments")
public class PostCommentController {

    private final PostCommentService postCommentService;

    @GetMapping
    public List<PostCommentDto> getComments(
            @RequestParam Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long currentUserId = userDetails == null ? null : userDetails.getId();
        return postCommentService.getComments(postId, currentUserId);
    }

    @PostMapping
    public ResponseEntity<PostCommentDto> create(
            @RequestBody PostCommentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        PostCommentDto created = postCommentService.create(
                request.getPostId(), userDetails.getId(), request.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{commentId}")
    public PostCommentDto update(
            @PathVariable Long commentId,
            @RequestBody PostCommentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return postCommentService.update(commentId, userDetails.getId(), request.getContent());
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        postCommentService.delete(commentId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
