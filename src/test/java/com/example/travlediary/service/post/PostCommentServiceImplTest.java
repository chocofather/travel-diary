package com.example.travlediary.service.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.model.PostComment;
import com.example.travlediary.repository.post.PostCommentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    void likesActiveRootCommentAndPassesCurrentUserId() {
        PostComment root = comment(30L, 8L);
        root.setLikes(4);
        when(postCommentMapper.findActiveCommentForUpdate(30L)).thenReturn(root);
        when(postCommentMapper.insertLike(7L, 30L)).thenReturn(1);

        service.likeComment(30L, 7L);

        verify(postCommentMapper).findActiveCommentForUpdate(30L);
        verify(postCommentMapper).insertLike(7L, 30L);
        assertThat(root.getLikes()).isEqualTo(4);
    }

    @Test
    void likesActiveReplyAndOwnCommentWhenInsertIsDuplicateNoop() {
        PostComment ownReply = comment(31L, 7L);
        ownReply.setParentCommentId(30L);
        when(postCommentMapper.findActiveCommentForUpdate(31L)).thenReturn(ownReply);
        when(postCommentMapper.insertLike(7L, 31L)).thenReturn(0);

        service.likeComment(31L, 7L);

        verify(postCommentMapper).insertLike(7L, 31L);
    }

    @Test
    void likeReturnsNotFoundForMissingOrDeletedComment() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.likeComment(30L, 7L));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.likeComment(31L, 7L));

        verify(postCommentMapper).findActiveCommentForUpdate(30L);
        verify(postCommentMapper).findActiveCommentForUpdate(31L);
        verify(postCommentMapper, never()).insertLike(any(), any());
    }

    @Test
    void unlikesActiveRootAndReplyIncludingMissingLikeNoop() {
        PostComment root = comment(30L, 8L);
        PostComment reply = comment(31L, 9L);
        reply.setParentCommentId(30L);
        when(postCommentMapper.findActiveCommentForUpdate(30L)).thenReturn(root);
        when(postCommentMapper.findActiveCommentForUpdate(31L)).thenReturn(reply);
        when(postCommentMapper.deleteLike(7L, 30L)).thenReturn(1);
        when(postCommentMapper.deleteLike(7L, 31L)).thenReturn(0);

        service.unlikeComment(30L, 7L);
        service.unlikeComment(31L, 7L);

        verify(postCommentMapper).deleteLike(7L, 30L);
        verify(postCommentMapper).deleteLike(7L, 31L);
    }

    @Test
    void unlikeReturnsNotFoundForMissingOrDeletedComment() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.unlikeComment(30L, 7L));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.unlikeComment(31L, 7L));

        verify(postCommentMapper).findActiveCommentForUpdate(30L);
        verify(postCommentMapper).findActiveCommentForUpdate(31L);
        verify(postCommentMapper, never()).deleteLike(any(), any());
    }

    @Test
    void pagedCommentsUseRootCountAndMergeAllRepliesWithoutChangingRootOrder() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.countRootCommentThreads(10L)).thenReturn(6);
        when(postCommentMapper.countActiveComments(10L)).thenReturn(9);
        PostCommentDto firstRoot = dto(30L, false);
        PostCommentDto secondRoot = dto(20L, false);
        PostCommentDto firstReply = dto(31L, false);
        firstReply.setParentCommentId(30L);
        PostCommentDto secondReply = dto(21L, false);
        secondReply.setParentCommentId(20L);
        when(postCommentMapper.findPagedRootComments(10L, null, "likes", 5, 0))
                .thenReturn(List.of(firstRoot, secondRoot));
        when(postCommentMapper.findRepliesForRootComments(10L, null, List.of(30L, 20L)))
                .thenReturn(List.of(secondReply, firstReply));

        PageResult<PostCommentDto> result = service.getCommentsPage(10L, null, 0, 5, "likes");

        assertThat(result.getContent()).extracting(PostCommentDto::getId)
                .containsExactly(30L, 31L, 20L, 21L);
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalCommentCount()).isEqualTo(9);
        assertThat(result.isLast()).isFalse();
    }

    @Test
    void outOfRangePagePreservesCountsAndSkipsContentQueries() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.countRootCommentThreads(10L)).thenReturn(6);
        when(postCommentMapper.countActiveComments(10L)).thenReturn(12);

        PageResult<PostCommentDto> result = service.getCommentsPage(10L, null, 2, 5, "latest");

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalCommentCount()).isEqualTo(12);
        verify(postCommentMapper, never()).findPagedRootComments(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void rootCommentLocationUsesTheFirstLatestPage() {
        when(postCommentMapper.findActiveRootIdForLocation(10L, 30L)).thenReturn(30L);
        when(postCommentMapper.countRootCommentsBefore(10L, 30L)).thenReturn(0);

        assertThat(service.getCommentLocation(10L, 30L))
                .hasValueSatisfying(location -> assertThat(location.getPage()).isEqualTo(1));
    }

    @Test
    void replyLocationUsesItsRootGroupAndCalculatesALaterPage() {
        when(postCommentMapper.findActiveRootIdForLocation(10L, 35L)).thenReturn(30L);
        when(postCommentMapper.countRootCommentsBefore(10L, 30L)).thenReturn(7);

        assertThat(service.getCommentLocation(10L, 35L))
                .hasValueSatisfying(location -> assertThat(location.getPage()).isEqualTo(2));
        verify(postCommentMapper).countRootCommentsBefore(10L, 30L);
    }

    @Test
    void deletedMissingOrOtherPostsCommentHasNoLocation() {
        when(postCommentMapper.findActiveRootIdForLocation(10L, 35L)).thenReturn(null);

        assertThat(service.getCommentLocation(10L, 35L)).isEmpty();
        verify(postCommentMapper, never()).countRootCommentsBefore(any(), any());
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
