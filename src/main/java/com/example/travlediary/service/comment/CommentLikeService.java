package com.example.travlediary.service.comment;

import com.example.travlediary.repository.comment.CommentLikeMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentLikeService {
    private final CommentLikeMapper commentLikeMapper;
    private final DestinationMapper destinationMapper; // 좋아요 수 업데이트용S

    // 댓글 좋아요 토글 처리
    @Transactional
    public boolean toggleLike(Long userId, Long commentId) {
        boolean alreadyLiked = commentLikeMapper.exists(userId, commentId);

        if (alreadyLiked) {
            commentLikeMapper.delete(userId, commentId);
            destinationMapper.decrementCommentLikeCount(commentId);
            return false;
        } else {
            commentLikeMapper.insert(userId, commentId);
            destinationMapper.incrementCommentLikeCount(commentId);
            return true;
        }
    }

}
