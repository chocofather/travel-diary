package com.example.travlediary.service.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.model.PostComment;
import com.example.travlediary.repository.post.PostCommentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCommentServiceImplTest {

    @Mock
    private PostCommentMapper postCommentMapper;

    private PostCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PostCommentServiceImpl(postCommentMapper);
    }

    @Test
    void createReturnsLatestDtoWithGeneratedId() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.insert(any(PostComment.class))).thenAnswer(invocation -> {
            PostComment comment = invocation.getArgument(0);
            assertThat(comment.getParentCommentId()).isNull();
            assertThat(comment.getReplyToCommentId()).isNull();
            comment.setId(30L);
            return 1;
        });
        PostCommentDto latest = dto(30L, true);
        when(postCommentMapper.findDtoById(30L, 7L)).thenReturn(latest);

        PostCommentDto result = service.create(10L, 7L, "  새 댓글  ", null);

        assertThat(result).isSameAs(latest);
        verify(postCommentMapper).findDtoById(30L, 7L);
    }

    @Test
    void createReturnsNotFoundWhenPostIsDeletedOrMissing() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(false);

        assertStatus(HttpStatus.NOT_FOUND, () -> service.create(10L, 7L, "댓글", null));
        verify(postCommentMapper, never()).insert(any());
    }

    @Test
    void createRejectsBlankAndOverTwoThousandCharacters() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "   ", null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "가".repeat(2_001), null));
        verify(postCommentMapper, never()).insert(any());
    }

    @Test
    void createReplyValidatesParentAndReturnsLatestDto() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.findActiveCommentForUpdate(20L)).thenReturn(comment(20L, 8L, 10L, null));
        when(postCommentMapper.insert(any(PostComment.class))).thenAnswer(invocation -> {
            PostComment reply = invocation.getArgument(0);
            assertThat(reply.getParentCommentId()).isEqualTo(20L);
            assertThat(reply.getReplyToCommentId()).isEqualTo(20L);
            reply.setId(30L);
            return 1;
        });
        PostCommentDto latest = dto(30L, true);
        latest.setParentCommentId(20L);
        when(postCommentMapper.findDtoById(30L, 7L)).thenReturn(latest);

        PostCommentDto result = service.create(10L, 7L, "답글", 20L);

        assertThat(result.getParentCommentId()).isEqualTo(20L);
        verify(postCommentMapper).findActiveCommentForUpdate(20L);
    }

    @Test
    void createReplyReturnsNotFoundForMissingOrDeletedParent() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.findActiveCommentForUpdate(20L)).thenReturn(null);

        assertStatus(HttpStatus.NOT_FOUND, () -> service.create(10L, 7L, "답글", 20L));
        verify(postCommentMapper, never()).insert(any());
    }

    @Test
    void createReplyRejectsParentFromAnotherPost() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.findActiveCommentForUpdate(20L)).thenReturn(comment(20L, 8L, 11L, null));

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "답글", 20L));
        verify(postCommentMapper, never()).insert(any());
    }

    @Test
    void createReplyToReplyKeepsRootGroupAndActualTarget() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        PostComment target = comment(20L, 8L, 10L, 15L);
        when(postCommentMapper.findActiveCommentForUpdate(20L)).thenReturn(target);
        when(postCommentMapper.findCommentForUpdate(15L)).thenReturn(comment(15L, 9L, 10L, null));
        when(postCommentMapper.insert(any(PostComment.class))).thenAnswer(invocation -> {
            PostComment reply = invocation.getArgument(0);
            assertThat(reply.getParentCommentId()).isEqualTo(15L);
            assertThat(reply.getReplyToCommentId()).isEqualTo(20L);
            reply.setId(30L);
            return 1;
        });
        when(postCommentMapper.findDtoById(30L, 7L)).thenReturn(dto(30L, true));

        service.create(10L, 7L, "중첩 답글", 20L);

        verify(postCommentMapper).findCommentForUpdate(15L);
    }

    @Test
    void createReplyToReplyRejectsMissingOrInvalidRoot() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.findActiveCommentForUpdate(20L))
                .thenReturn(comment(20L, 8L, 10L, 15L));

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "답글", 20L));
        verify(postCommentMapper, never()).insert(any());
    }

    @Test
    void createReplyToReplyRejectsRootFromAnotherPost() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.findActiveCommentForUpdate(20L))
                .thenReturn(comment(20L, 8L, 10L, 15L));
        when(postCommentMapper.findCommentForUpdate(15L))
                .thenReturn(comment(15L, 9L, 11L, null));

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "답글", 20L));
        verify(postCommentMapper, never()).insert(any());
    }

    @Test
    void updateReturnsNotFoundWhenCommentIsDeletedOrMissing() {
        when(postCommentMapper.findActiveComment(30L)).thenReturn(null);

        assertStatus(HttpStatus.NOT_FOUND, () -> service.update(30L, 7L, "수정"));
        verify(postCommentMapper, never()).updateContent(any(), any(), any());
    }

    @Test
    void updateReturnsForbiddenForAnotherUsersComment() {
        when(postCommentMapper.findActiveComment(30L)).thenReturn(comment(30L, 8L));

        assertStatus(HttpStatus.FORBIDDEN, () -> service.update(30L, 7L, "수정"));
        verify(postCommentMapper, never()).updateContent(any(), any(), any());
    }

    @Test
    void updateReturnsLatestDto() {
        when(postCommentMapper.findActiveComment(30L)).thenReturn(comment(30L, 7L));
        when(postCommentMapper.updateContent(30L, 7L, "수정 댓글")).thenReturn(1);
        PostCommentDto latest = dto(30L, true);
        when(postCommentMapper.findDtoById(30L, 7L)).thenReturn(latest);

        assertThat(service.update(30L, 7L, "수정 댓글")).isSameAs(latest);
    }

    @Test
    void updateUsesTheSameContentValidationAsCreate() {
        when(postCommentMapper.findActiveComment(30L)).thenReturn(comment(30L, 7L));

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.update(30L, 7L, "  "));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.update(30L, 7L, "가".repeat(2_001)));
        verify(postCommentMapper, never()).updateContent(any(), any(), any());
    }

    @Test
    void deleteDistinguishesNotFoundAndForbidden() {
        when(postCommentMapper.findActiveComment(30L)).thenReturn(null);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.delete(30L, 7L));

        when(postCommentMapper.findActiveComment(31L)).thenReturn(comment(31L, 8L));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.delete(31L, 7L));
    }

    @Test
    void deleteUsesCurrentUserCondition() {
        when(postCommentMapper.findActiveComment(30L)).thenReturn(comment(30L, 7L));
        when(postCommentMapper.softDelete(30L, 7L)).thenReturn(1);

        service.delete(30L, 7L);

        verify(postCommentMapper).softDelete(30L, 7L);
    }

    @Test
    void deletedRootDtoDoesNotExposeMaskedFields() throws Exception {
        PostCommentDto deletedRoot = new PostCommentDto();
        deletedRoot.setId(30L);
        deletedRoot.setPostId(10L);
        deletedRoot.setDeleted(true);

        String json = new ObjectMapper().writeValueAsString(deletedRoot);

        assertThat(json)
                .doesNotContain("content")
                .doesNotContain("writerNickname")
                .doesNotContain("updatedAt")
                .doesNotContain("myComment");
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(status));
    }

    private PostComment comment(Long id, Long userId) {
        return comment(id, userId, null, null);
    }

    private PostComment comment(Long id, Long userId, Long postId, Long parentCommentId) {
        PostComment comment = new PostComment();
        comment.setId(id);
        comment.setUserId(userId);
        comment.setPostId(postId);
        comment.setParentCommentId(parentCommentId);
        return comment;
    }

    private PostCommentDto dto(Long id, boolean myComment) {
        PostCommentDto dto = new PostCommentDto();
        dto.setId(id);
        dto.setWriterNickname("작성자");
        dto.setMyComment(myComment);
        return dto;
    }
}
