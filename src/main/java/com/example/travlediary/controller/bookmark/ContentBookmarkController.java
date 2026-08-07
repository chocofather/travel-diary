package com.example.travlediary.controller.bookmark;

import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.bookmark.ContentBookmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookmarks")
public class ContentBookmarkController {

    private final ContentBookmarkService contentBookmarkService;

    @PostMapping("/posts/{postId}")
    public ResponseEntity<Void> bookmarkPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        contentBookmarkService.bookmarkPost(postId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> unbookmarkPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        contentBookmarkService.unbookmarkPost(postId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/courses/{courseId}")
    public ResponseEntity<Void> bookmarkCourse(
            @PathVariable Long courseId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        contentBookmarkService.bookmarkCourse(courseId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/courses/{courseId}")
    public ResponseEntity<Void> unbookmarkCourse(
            @PathVariable Long courseId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        contentBookmarkService.unbookmarkCourse(courseId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
