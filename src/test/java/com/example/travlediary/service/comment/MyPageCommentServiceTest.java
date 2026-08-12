package com.example.travlediary.service.comment;

import com.example.travlediary.dto.MyPageCommentDto;
import com.example.travlediary.dto.MyPageCommentPageDto;
import com.example.travlediary.repository.comment.MyPageCommentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyPageCommentServiceTest {

    @Mock
    private MyPageCommentMapper myPageCommentMapper;

    @Test
    void normalizesSupportedFiltersAndUsesThePrincipalUserId() {
        MyPageCommentService service = new MyPageCommentService(myPageCommentMapper);
        MyPageCommentDto comment = new MyPageCommentDto();
        comment.setCommentType("post");
        when(myPageCommentMapper.countMyComments(7L, "post")).thenReturn(21);
        when(myPageCommentMapper.findMyComments(7L, "post", 0L, 10))
                .thenReturn(List.of(comment));

        MyPageCommentPageDto result = service.getMyComments(7L, "POST", 0);

        assertThat(result.getType()).isEqualTo("post");
        assertThat(result.getCurrentPage()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getTotalCount()).isEqualTo(21);
        assertThat(result.getComments()).containsExactly(comment);
        verify(myPageCommentMapper).countMyComments(7L, "post");
        verify(myPageCommentMapper).findMyComments(7L, "post", 0L, 10);
    }

    @Test
    void keepsTenItemsPerPageAndCalculatesTheOffset() {
        MyPageCommentService service = new MyPageCommentService(myPageCommentMapper);
        when(myPageCommentMapper.countMyComments(7L, "course")).thenReturn(12);
        when(myPageCommentMapper.findMyComments(7L, "course", 10L, 10))
                .thenReturn(List.of());

        MyPageCommentPageDto result = service.getMyComments(7L, "course", 2);

        assertThat(result.getCurrentPage()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        verify(myPageCommentMapper).findMyComments(7L, "course", 10L, 10);
    }

    @Test
    void invalidFilterFallsBackToAllAndZeroResultsSkipTheListQuery() {
        MyPageCommentService service = new MyPageCommentService(myPageCommentMapper);
        when(myPageCommentMapper.countMyComments(7L, null)).thenReturn(0);

        MyPageCommentPageDto result = service.getMyComments(7L, "abc", -3);

        assertThat(result.getType()).isEqualTo("all");
        assertThat(result.getCurrentPage()).isEqualTo(1);
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.getComments()).isEmpty();
        verify(myPageCommentMapper).countMyComments(7L, null);
        verify(myPageCommentMapper, never()).findMyComments(7L, null, 0L, 10);
    }

    @Test
    void acceptsEverySupportedFilter() {
        MyPageCommentService service = new MyPageCommentService(myPageCommentMapper);

        for (String type : List.of("destination", "post", "course")) {
            when(myPageCommentMapper.countMyComments(7L, type)).thenReturn(0);

            MyPageCommentPageDto result = service.getMyComments(7L, type, 1);

            assertThat(result.getType()).isEqualTo(type);
            verify(myPageCommentMapper).countMyComments(7L, type);
        }

        when(myPageCommentMapper.countMyComments(7L, null)).thenReturn(0);
        assertThat(service.getMyComments(7L, "all", 1).getType()).isEqualTo("all");
        verify(myPageCommentMapper).countMyComments(7L, null);
    }

    @Test
    void rejectsMissingAuthenticatedUserIdBeforeQuerying() {
        MyPageCommentService service = new MyPageCommentService(myPageCommentMapper);

        assertThatThrownBy(() -> service.getMyComments(null, "all", 1))
                .isInstanceOf(IllegalArgumentException.class);

        verify(myPageCommentMapper, never()).countMyComments(null, null);
    }
}
