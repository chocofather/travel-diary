package com.example.travlediary.service.comment;

import com.example.travlediary.dto.CommentDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.model.DestinationComment;
import com.example.travlediary.model.User;
import com.example.travlediary.repository.comment.DestinationCommentImageMapper;
import com.example.travlediary.repository.comment.DestinationCommentMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationCommentServicePagingTest {

    @Mock
    private DestinationMapper destinationMapper;
    @Mock
    private DestinationCommentMapper commentMapper;
    @Mock
    private DestinationCommentImageMapper commentImageMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FileUploadService fileUploadService;

    private DestinationCommentService service;

    @BeforeEach
    void setUp() {
        service = new DestinationCommentService(
                destinationMapper, commentMapper, commentImageMapper, userMapper, fileUploadService);
    }

    @Test
    void outOfRangePageKeepsThreadAndActiveCommentTotals() {
        when(commentMapper.countRootComments(10L)).thenReturn(6);
        when(commentMapper.countByDestinationId(10L)).thenReturn(12);

        PageResult<CommentDto> result = service.getCommentsPaged(10L, null, 2, 5, "latest");

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalCommentCount()).isEqualTo(12);
        verify(commentMapper, never()).findPagedParentComments(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    void unexpectedEmptyContentPageDoesNotOverwriteKnownTotals() {
        when(commentMapper.countRootComments(10L)).thenReturn(6);
        when(commentMapper.countByDestinationId(10L)).thenReturn(9);
        when(commentMapper.findPagedParentComments(10L, 0, 5, "latest")).thenReturn(List.of());

        PageResult<CommentDto> result = service.getCommentsPaged(10L, null, 0, 5, "unknown");

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalCommentCount()).isEqualTo(9);
        verify(commentMapper).findPagedParentComments(10L, 0, 5, "latest");
    }

    @Test
    void moderatedParentAndReplyKeepTheirFlagOnThePagedResponse() {
        when(commentMapper.countRootComments(10L)).thenReturn(1);
        when(commentMapper.countByDestinationId(10L)).thenReturn(1);
        when(commentMapper.findPagedParentComments(10L, 0, 5, "latest"))
                .thenReturn(List.of(comment(1L, null, true)));
        when(commentMapper.findRepliesForParents(10L, List.of(1L)))
                .thenReturn(List.of(comment(2L, 1L, true), comment(3L, 1L, false)));

        PageResult<CommentDto> result = service.getCommentsPaged(10L, null, 0, 5, "latest");

        // 부모/대댓글 모두 플레이스홀더 렌더링에 필요한 moderated 가 유지된다
        assertThat(result.getContent())
                .extracting(CommentDto::getId, CommentDto::isModerated)
                .containsExactly(
                        tuple(1L, true),
                        tuple(2L, true),
                        tuple(3L, false));
    }

    @Test
    void rootAndReplyLocationsUseTheVisibleRootGroup() {
        when(commentMapper.findActiveRootIdForLocation(10L, 30L)).thenReturn(30L);
        when(commentMapper.countRootCommentsBefore(10L, 30L)).thenReturn(0);
        when(commentMapper.findActiveRootIdForLocation(10L, 36L)).thenReturn(31L);
        when(commentMapper.countRootCommentsBefore(10L, 31L)).thenReturn(6);

        assertThat(service.getCommentLocation(10L, 30L))
                .hasValueSatisfying(location -> assertThat(location.getPage()).isEqualTo(1));
        assertThat(service.getCommentLocation(10L, 36L))
                .hasValueSatisfying(location -> assertThat(location.getPage()).isEqualTo(2));
        verify(commentMapper).countRootCommentsBefore(10L, 31L);
    }

    @Test
    void deletedTargetWrongDestinationOrReplyBelowDeletedRootHasNoLocation() {
        when(commentMapper.findActiveRootIdForLocation(10L, 36L)).thenReturn(null);

        assertThat(service.getCommentLocation(10L, 36L)).isEmpty();
        verify(commentMapper, never()).countRootCommentsBefore(anyLong(), anyLong());
    }

    /** 관리자 조치 댓글은 deleted = 1 이면서 moderated = true 로 내려온다. */
    private DestinationComment comment(long id, Long parentId, boolean moderated) {
        DestinationComment comment = new DestinationComment();
        comment.setId(id);
        comment.setParentCommentId(parentId);
        comment.setContent(moderated ? null : "내용 " + id);
        comment.setLikes(0);
        comment.setDeleted(moderated);
        comment.setModerated(moderated);
        comment.setUserId(7L);
        comment.setDestinationId(10L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);

        User writer = new User();
        writer.setId(7L);
        writer.setNickname(moderated ? null : "여행자");
        comment.setWriter(writer);
        return comment;
    }
}
