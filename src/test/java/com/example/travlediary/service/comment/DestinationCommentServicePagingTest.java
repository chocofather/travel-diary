package com.example.travlediary.service.comment;

import com.example.travlediary.dto.CommentDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.repository.comment.DestinationCommentMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private UserMapper userMapper;

    private DestinationCommentService service;

    @BeforeEach
    void setUp() {
        service = new DestinationCommentService(destinationMapper, commentMapper, userMapper);
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
}
