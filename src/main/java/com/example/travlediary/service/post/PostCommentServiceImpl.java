package com.example.travlediary.service.post;

import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.model.PostComment;
import com.example.travlediary.repository.post.PostCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PostCommentServiceImpl implements PostCommentService {

    private static final int MAX_CONTENT_LENGTH = 2_000;

    private final PostCommentMapper postCommentMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PostCommentDto> getComments(Long postId, Long currentUserId) {
        requireActivePost(postId);
        return postCommentMapper.findByPostId(postId, currentUserId);
    }

    @Override
    @Transactional
    public PostCommentDto create(Long postId, Long userId, String content, Long replyToCommentId) {
        requireActivePost(postId);
        String validatedContent = validateContent(content);
        Long parentCommentId = resolveParentCommentId(postId, replyToCommentId);

        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(validatedContent);
        comment.setParentCommentId(parentCommentId);
        comment.setReplyToCommentId(replyToCommentId);

        if (postCommentMapper.insert(comment) != 1 || comment.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "댓글 등록에 실패했습니다.");
        }
        return requireLatestDto(comment.getId(), userId);
    }

    @Override
    @Transactional
    public PostCommentDto update(Long commentId, Long userId, String content) {
        PostComment comment = requireOwnedActiveComment(commentId, userId);
        String validatedContent = validateContent(content);

        if (postCommentMapper.updateContent(commentId, userId, validatedContent) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        return requireLatestDto(comment.getId(), userId);
    }

    @Override
    @Transactional
    public void delete(Long commentId, Long userId) {
        requireOwnedActiveComment(commentId, userId);
        if (postCommentMapper.softDelete(commentId, userId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
    }

    @Override
    @Transactional
    public void likeComment(Long commentId, Long userId) {
        requireLockedActiveComment(commentId);
        postCommentMapper.insertLike(userId, commentId);
    }

    @Override
    @Transactional
    public void unlikeComment(Long commentId, Long userId) {
        requireLockedActiveComment(commentId);
        postCommentMapper.deleteLike(userId, commentId);
    }

    private void requireActivePost(Long postId) {
        if (postId == null || !postCommentMapper.existsActivePost(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
    }

    private PostComment requireOwnedActiveComment(Long commentId, Long userId) {
        PostComment comment = commentId == null ? null : postCommentMapper.findActiveComment(commentId);
        if (comment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "댓글 변경 권한이 없습니다.");
        }
        return comment;
    }

    private PostComment requireLockedActiveComment(Long commentId) {
        PostComment comment = commentId == null
                ? null
                : postCommentMapper.findActiveCommentForUpdate(commentId);
        if (comment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        return comment;
    }

    private Long resolveParentCommentId(Long postId, Long replyToCommentId) {
        if (replyToCommentId == null) {
            return null;
        }

        PostComment target = postCommentMapper.findActiveCommentForUpdate(replyToCommentId);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "답글 대상 댓글을 찾을 수 없습니다.");
        }
        if (!Objects.equals(target.getPostId(), postId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 게시글의 댓글에만 답글을 작성할 수 있습니다.");
        }

        if (target.getParentCommentId() == null) {
            return target.getId();
        }

        PostComment root = postCommentMapper.findCommentForUpdate(target.getParentCommentId());
        if (root == null || root.getParentCommentId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "원댓글 구조가 올바르지 않습니다.");
        }
        if (!Objects.equals(root.getPostId(), postId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 게시글의 원댓글만 사용할 수 있습니다.");
        }
        return root.getId();
    }

    private String validateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해 주세요.");
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글은 2,000자 이하로 입력해 주세요.");
        }
        return trimmed;
    }

    private PostCommentDto requireLatestDto(Long commentId, Long currentUserId) {
        PostCommentDto dto = postCommentMapper.findDtoById(commentId, currentUserId);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        return dto;
    }
}
