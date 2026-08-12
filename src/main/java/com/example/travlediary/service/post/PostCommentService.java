package com.example.travlediary.service.post;

import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.dto.CommentLocationDto;
import com.example.travlediary.dto.PageResult;

import java.util.List;
import java.util.Optional;

public interface PostCommentService {

    List<PostCommentDto> getComments(Long postId, Long currentUserId);

    PageResult<PostCommentDto> getCommentsPage(Long postId, Long currentUserId,
                                               int page, int size, String sort);

    Optional<CommentLocationDto> getCommentLocation(Long postId, Long commentId);

    PostCommentDto create(Long postId, Long userId, String content, Long replyToCommentId);

    PostCommentDto update(Long commentId, Long userId, String content);

    void delete(Long commentId, Long userId);

    void likeComment(Long commentId, Long userId);

    void unlikeComment(Long commentId, Long userId);
}
