package com.example.travlediary.service.post;

import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.dto.CommentLocationDto;
import com.example.travlediary.dto.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface PostCommentService {

    List<PostCommentDto> getComments(Long postId, Long currentUserId);

    PageResult<PostCommentDto> getCommentsPage(Long postId, Long currentUserId,
                                               int page, int size, String sort);

    Optional<CommentLocationDto> getCommentLocation(Long postId, Long commentId);

    /** 댓글/답글 등록. 사진은 최대 3장까지 함께 저장한다. */
    PostCommentDto create(Long postId, Long userId, String content, Long replyToCommentId,
                          List<MultipartFile> images);

    PostCommentDto update(Long commentId, Long userId, String content);

    void delete(Long commentId, Long userId);

    void likeComment(Long commentId, Long userId);

    void unlikeComment(Long commentId, Long userId);
}
