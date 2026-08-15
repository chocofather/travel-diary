package com.example.travlediary.service.user;

import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.comment.CommentLikeMapper;
import com.example.travlediary.repository.course.CourseCommentMapper;
import com.example.travlediary.repository.post.PostCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 탈퇴 처리에 공통으로 쓰는 개인 흔적 정리와 익명화 값 생성.
 * 회원 본인 탈퇴(MyPageAccountService)와 관리자 강제탈퇴가 같은 규칙을 쓰도록 모아둔다.
 * 작성한 글과 댓글은 여기서 삭제하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AccountAnonymizationService {

    private final BookmarkMapper bookmarkMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final PostCommentMapper postCommentMapper;
    private final CourseCommentMapper courseCommentMapper;

    /** 좋아요·북마크 등 개인 활동 흔적만 정리한다. */
    public void clearPersonalTraces(Long userId) {
        commentLikeMapper.decrementDestinationLikeCountsByUserId(userId);
        commentLikeMapper.deleteAllByUserId(userId);
        postCommentMapper.deleteAllLikesByUserId(userId);
        courseCommentMapper.deleteAllLikesByUserId(userId);
        bookmarkMapper.deleteAllByUserId(userId);
    }

    public String anonymizedEmail(Long userId) {
        String random = UUID.randomUUID().toString().replace("-", "");
        return "withdrawn-" + userId + "-" + random + "@example.invalid";
    }

    public String anonymizedNickname() {
        return "탈퇴" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}