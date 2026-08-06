package com.example.travlediary.service.post;

import com.example.travlediary.dto.PostCommentDto;

import java.util.List;

public interface PostCommentService {

    List<PostCommentDto> getComments(Long postId, Long currentUserId);

    PostCommentDto create(Long postId, Long userId, String content, Long parentCommentId);

    PostCommentDto update(Long commentId, Long userId, String content);

    void delete(Long commentId, Long userId);
}
