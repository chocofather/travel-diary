package com.example.travlediary.service.post;

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
            comment.setId(30L);
            return 1;
        });
        PostCommentDto latest = dto(30L, true);
        when(postCommentMapper.findDtoById(30L, 7L)).thenReturn(latest);

        PostCommentDto result = service.create(10L, 7L, "  새 댓글  ");

        assertThat(result).isSameAs(latest);
        verify(postCommentMapper).findDtoById(30L, 7L);
    }

    @Test
    void createReturnsNotFoundWhenPostIsDeletedOrMissing() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(false);

        assertStatus(HttpStatus.NOT_FOUND, () -> service.create(10L, 7L, "댓글"));
        verify(postCommentMapper, never()).insert(any());
    }

    @Test
    void createRejectsBlankAndOverTwoThousandCharacters() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "   "));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "가".repeat(2_001)));
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

    private void assertStatus(HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(status));
    }

    private PostComment comment(Long id, Long userId) {
        PostComment comment = new PostComment();
        comment.setId(id);
        comment.setUserId(userId);
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
