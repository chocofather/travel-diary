package com.example.travlediary.controller.destination;


import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.destination.DestinationBookmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookmarks")
@RequiredArgsConstructor
public class DestinationBookmarkController {

    private final DestinationBookmarkService destinationBookmarkService;

    // 찜 토글
    @PostMapping
    public ResponseEntity<String> toggleBookmark(
            @RequestParam Long destinationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("로그인 필요");
        }
        boolean added = destinationBookmarkService.toggleBookmark(
                userDetails.getId(), destinationId);
        return ResponseEntity.ok(added ? "bookmarked" : "unbookmarked");
    }

    // 찜 여부 조회
    @GetMapping("/check")
    public ResponseEntity<Boolean> isBookmarked(
            @RequestParam Long destinationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.ok(false);
        }
        return ResponseEntity.ok(destinationBookmarkService.isBookmarked(
                userDetails.getId(), destinationId));
    }
    // 찜 개수 조회 (익명도 OK)
    @GetMapping("/count")
    public ResponseEntity<Integer> getBookmarkCount(@RequestParam Long destinationId) {
        return ResponseEntity.ok(destinationBookmarkService.getBookmarkCount(destinationId));
    }

    @DeleteMapping("/destinations/{destinationId}")
    public ResponseEntity<Void> removeBookmark(
            @PathVariable Long destinationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        destinationBookmarkService.removeBookmark(userDetails.getId(), destinationId);
        return ResponseEntity.noContent().build();
    }
}
