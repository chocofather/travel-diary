package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.model.CourseComment;
import com.example.travlediary.repository.course.CourseCommentMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseCommentServiceImplTest {

    @Mock
    private CourseCommentMapper mapper;

    private CourseCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CourseCommentServiceImpl(mapper);
    }

    @Test
    void getsActiveCourseCommentsForGuest() {
        CourseCommentDto comment = dto(30L, false);
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
        when(mapper.findByCourseId(10L, null)).thenReturn(List.of(comment));

        assertThat(service.getComments(10L, null)).containsExactly(comment);
        assertThat(comment.isMyComment()).isFalse();
    }

    @Test
    void getsActiveCourseCommentsAndMarksOnlyCurrentUsersComment() {
        CourseCommentDto mine = dto(30L, true);
        CourseCommentDto other = dto(31L, false);
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
        when(mapper.findByCourseId(10L, 7L)).thenReturn(List.of(mine, other));

        List<CourseCommentDto> result = service.getComments(10L, 7L);

        assertThat(result).extracting(CourseCommentDto::isMyComment).containsExactly(true, false);
        verify(mapper).findByCourseId(10L, 7L);
    }

    @Test
    void missingOrDeletedCourseReadReturnsNotFound() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getComments(10L, null));
        verify(mapper, never()).findByCourseId(any(), any());
    }

    @Test
    void createsTrimmedRootCommentAndReturnsLatestDto() {
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
        when(mapper.insert(any(CourseComment.class))).thenAnswer(invocation -> {
            CourseComment comment = invocation.getArgument(0);
            assertThat(comment.getCourseId()).isEqualTo(10L);
            assertThat(comment.getUserId()).isEqualTo(7L);
            assertThat(comment.getContent()).isEqualTo("새 댓글");
            assertThat(comment.getParentCommentId()).isNull();
            comment.setId(30L);
            return 1;
        });
        CourseCommentDto latest = dto(30L, true);
        when(mapper.findDtoById(30L, 7L)).thenReturn(latest);

        assertThat(service.create(10L, 7L, "  새 댓글  ", null)).isSameAs(latest);
    }

    @Test
    void insertZeroRowsFails() {
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
        when(mapper.insert(any())).thenReturn(0);

        assertStatus(HttpStatus.INTERNAL_SERVER_ERROR, () -> service.create(10L, 7L, "댓글", null));
    }

    @Test
    void missingGeneratedIdFails() {
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
        when(mapper.insert(any())).thenReturn(1);

        assertStatus(HttpStatus.INTERNAL_SERVER_ERROR, () -> service.create(10L, 7L, "댓글", null));
    }

    @Test
    void missingOrDeletedCourseCreateReturnsNotFoundWithoutInsert() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.create(10L, 7L, "댓글", null));
        verify(mapper, never()).insert(any());
    }

    @Test
    void createRejectsBlankAndOverTwoThousandCharacters() {
        when(mapper.existsActiveCourse(10L)).thenReturn(true);

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "   ", null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "가".repeat(2_001), null));
        verify(mapper, never()).insert(any());
    }

    @Test
    void createsTrimmedReplyAfterLockingRootParent() {
        CourseComment parent = comment(20L, 8L);
        parent.setCourseId(10L);
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
        when(mapper.findActiveCommentForUpdate(20L)).thenReturn(parent);
        when(mapper.insert(any(CourseComment.class))).thenAnswer(invocation -> {
            CourseComment reply = invocation.getArgument(0);
            assertThat(reply.getParentCommentId()).isEqualTo(20L);
            assertThat(reply.getContent()).isEqualTo("대댓글");
            reply.setId(30L);
            return 1;
        });
        CourseCommentDto latest = dto(30L, true);
        latest.setParentCommentId(20L);
        when(mapper.findDtoById(30L, 7L)).thenReturn(latest);

        CourseCommentDto result = service.create(10L, 7L, "  대댓글  ", 20L);

        assertThat(result.getParentCommentId()).isEqualTo(20L);
        verify(mapper).findActiveCommentForUpdate(20L);
    }

    @Test
    void replyRejectsBlankAndOverTwoThousandCharactersBeforeParentLookup() {
        when(mapper.existsActiveCourse(10L)).thenReturn(true);

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "   ", 20L));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "가".repeat(2_001), 20L));
        verify(mapper, never()).findActiveCommentForUpdate(any());
        verify(mapper, never()).insert(any());
    }

    @Test
    void missingOrDeletedParentReturnsNotFoundWithoutInsert() {
        when(mapper.existsActiveCourse(10L)).thenReturn(true);

        assertStatus(HttpStatus.NOT_FOUND, () -> service.create(10L, 7L, "대댓글", 20L));
        verify(mapper, never()).insert(any());
    }

    @Test
    void parentFromAnotherCourseReturnsBadRequestWithoutInsert() {
        CourseComment parent = comment(20L, 8L);
        parent.setCourseId(11L);
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
        when(mapper.findActiveCommentForUpdate(20L)).thenReturn(parent);

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "대댓글", 20L));
        verify(mapper, never()).insert(any());
    }

    @Test
    void replyCannotBeUsedAsParent() {
        CourseComment parent = comment(20L, 8L);
        parent.setCourseId(10L);
        parent.setParentCommentId(15L);
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
        when(mapper.findActiveCommentForUpdate(20L)).thenReturn(parent);

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(10L, 7L, "중첩 답글", 20L));
        verify(mapper, never()).insert(any());
    }

    @Test
    void replyInsertZeroRowsAndMissingGeneratedIdFail() {
        CourseComment parent = comment(20L, 8L);
        parent.setCourseId(10L);
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
        when(mapper.findActiveCommentForUpdate(20L)).thenReturn(parent);
        when(mapper.insert(any())).thenReturn(0, 1);

        assertStatus(HttpStatus.INTERNAL_SERVER_ERROR, () -> service.create(10L, 7L, "대댓글", 20L));
        assertStatus(HttpStatus.INTERNAL_SERVER_ERROR, () -> service.create(10L, 7L, "대댓글", 20L));
    }

    @Test
    void updatesOwnedCommentAndReturnsLatestDto() {
        when(mapper.findActiveComment(30L)).thenReturn(comment(30L, 7L));
        when(mapper.updateContent(30L, 7L, "수정 댓글")).thenReturn(1);
        CourseCommentDto latest = dto(30L, true);
        when(mapper.findDtoById(30L, 7L)).thenReturn(latest);

        assertThat(service.update(30L, 7L, "  수정 댓글  ")).isSameAs(latest);
    }

    @Test
    void missingOrDeletedCommentUpdateReturnsNotFound() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.update(30L, 7L, "수정"));
        verify(mapper, never()).updateContent(any(), any(), any());
    }

    @Test
    void anotherUsersCommentUpdateReturnsForbidden() {
        when(mapper.findActiveComment(30L)).thenReturn(comment(30L, 8L));

        assertStatus(HttpStatus.FORBIDDEN, () -> service.update(30L, 7L, "수정"));
        verify(mapper, never()).updateContent(any(), any(), any());
    }

    @Test
    void updateZeroRowsReturnsNotFound() {
        when(mapper.findActiveComment(30L)).thenReturn(comment(30L, 7L));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.update(30L, 7L, "수정"));
    }

    @Test
    void ownedReplyCanBeUpdated() {
        CourseComment reply = comment(30L, 7L);
        reply.setParentCommentId(20L);
        when(mapper.findActiveComment(30L)).thenReturn(reply);
        when(mapper.updateContent(30L, 7L, "수정 대댓글")).thenReturn(1);
        when(mapper.findDtoById(30L, 7L)).thenReturn(dto(30L, true));

        service.update(30L, 7L, "수정 대댓글");

        verify(mapper).updateContent(30L, 7L, "수정 대댓글");
    }

    @Test
    void deletesOwnedComment() {
        when(mapper.findActiveComment(30L)).thenReturn(comment(30L, 7L));
        when(mapper.softDelete(30L, 7L)).thenReturn(1);

        service.delete(30L, 7L);

        verify(mapper).softDelete(30L, 7L);
    }

    @Test
    void missingOrDeletedCommentDeleteReturnsNotFound() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.delete(30L, 7L));
        verify(mapper, never()).softDelete(any(), any());
    }

    @Test
    void anotherUsersCommentDeleteReturnsForbidden() {
        when(mapper.findActiveComment(30L)).thenReturn(comment(30L, 8L));

        assertStatus(HttpStatus.FORBIDDEN, () -> service.delete(30L, 7L));
        verify(mapper, never()).softDelete(any(), any());
    }

    @Test
    void deleteZeroRowsReturnsNotFound() {
        when(mapper.findActiveComment(30L)).thenReturn(comment(30L, 7L));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.delete(30L, 7L));
    }

    @Test
    void ownedReplyCanBeSoftDeleted() {
        CourseComment reply = comment(30L, 7L);
        reply.setParentCommentId(20L);
        when(mapper.findActiveComment(30L)).thenReturn(reply);
        when(mapper.softDelete(30L, 7L)).thenReturn(1);

        service.delete(30L, 7L);

        verify(mapper).softDelete(30L, 7L);
    }

    private CourseComment comment(Long id, Long userId) {
        CourseComment comment = new CourseComment();
        comment.setId(id);
        comment.setUserId(userId);
        return comment;
    }

    private CourseCommentDto dto(Long id, boolean myComment) {
        CourseCommentDto dto = new CourseCommentDto();
        dto.setId(id);
        dto.setMyComment(myComment);
        return dto;
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(status));
    }
}
