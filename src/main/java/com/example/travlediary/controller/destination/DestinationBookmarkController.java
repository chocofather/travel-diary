package com.example.travlediary.controller.destination;


import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
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
    private final UserMapper userMapper; // 또는 UserService

    // 찜 토글
    @PostMapping
    public ResponseEntity<String> toggleBookmark(
            @RequestParam Long destinationId,
            java.security.Principal principal    // ← 로그인 정보
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body("로그인 필요");
        }
        // principal.getName() 은 username
        User user = userMapper.findByUsername(principal.getName());
        boolean added = destinationBookmarkService.toggleBookmark(user.getId(), destinationId);
        return ResponseEntity.ok(added ? "bookmarked" : "unbookmarked");
    }

    // 찜 여부 조회
    @GetMapping("/check")
    public ResponseEntity<Boolean> isBookmarked(
            @RequestParam Long destinationId,
            java.security.Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity.ok(false);
        }
        User user = userMapper.findByUsername(principal.getName());
        if (user == null) {
            // 로그 한 줄 남기기
            System.out.println("isBookmarked: user not found for principal " + principal.getName());
            return ResponseEntity.ok(false);
        }
        return ResponseEntity.ok(destinationBookmarkService.isBookmarked(user.getId(), destinationId));
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
