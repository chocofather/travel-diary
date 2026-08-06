package com.example.travlediary.service.post;

import com.example.travlediary.dto.PostDetailDto;
import com.example.travlediary.dto.PostEditDto;
import com.example.travlediary.dto.PostUpdateRequest;
import com.example.travlediary.model.PostType;
import com.example.travlediary.model.UserPost;
import com.example.travlediary.repository.post.PostMapper;
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
class PostServiceImplTest {

    @Mock
    private PostMapper postMapper;

    private PostServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PostServiceImpl(postMapper, new PostContentSanitizer());
    }

    @Test
    void editLookupDoesNotIncrementViews() {
        when(postMapper.findActivePost(10L)).thenReturn(post(10L, 7L));
        PostEditDto edit = new PostEditDto();
        edit.setId(10L);
        edit.setContent("<p>기존 본문</p>");
        when(postMapper.findPostForEdit(10L)).thenReturn(edit);

        assertThat(service.getPostForEdit(10L, 7L).getContent()).isEqualTo("<p>기존 본문</p>");

        verify(postMapper, never()).incrementViews(any());
    }

    @Test
    void editLookupDistinguishesNotFoundAndForbidden() {
        when(postMapper.findActivePost(10L)).thenReturn(null);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getPostForEdit(10L, 7L));

        when(postMapper.findActivePost(11L)).thenReturn(post(11L, 8L));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.getPostForEdit(11L, 7L));
    }

    @Test
    void updateUsesOwnerAndSanitizedValidatedFields() {
        when(postMapper.findActivePost(10L)).thenReturn(post(10L, 7L));
        when(postMapper.updatePost(10L, 7L, "수정 제목", PostType.TIP, "<p>수정 본문</p>"))
                .thenReturn(1);

        service.updatePost(10L, 7L, request("  수정 제목  ", PostType.TIP, "<p>수정 본문</p>"));

        verify(postMapper).updatePost(10L, 7L, "수정 제목", PostType.TIP, "<p>수정 본문</p>");
        verify(postMapper, never()).insertPostImage(any());
    }

    @Test
    void updateDistinguishesNotFoundAndForbidden() {
        PostUpdateRequest request = request("제목", PostType.QUESTION, "<p>본문</p>");
        when(postMapper.findActivePost(10L)).thenReturn(null);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.updatePost(10L, 7L, request));

        when(postMapper.findActivePost(11L)).thenReturn(post(11L, 8L));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.updatePost(11L, 7L, request));
        verify(postMapper, never()).updatePost(any(), any(), any(), any(), any());
    }

    @Test
    void updateRejectsBlankAndOversizedInput() {
        when(postMapper.findActivePost(10L)).thenReturn(post(10L, 7L));

        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.updatePost(10L, 7L, request(" ", PostType.QUESTION, "<p>본문</p>")));
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.updatePost(10L, 7L, request("가".repeat(256), PostType.QUESTION, "<p>본문</p>")));
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.updatePost(10L, 7L, request("제목", PostType.QUESTION, "<p><br></p>")));
        verify(postMapper, never()).updatePost(any(), any(), any(), any(), any());
    }

    @Test
    void createUsesTheSameValidationPolicy() {
        UserPost post = post(10L, 7L);
        post.setTitle(" ");
        post.setPostType(PostType.QUESTION);
        post.setContent("<p>본문</p>");

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createPost(post, List.of()));
        verify(postMapper, never()).insertPost(any());
    }

    @Test
    void deleteOnlySoftDeletesOwnedActivePost() {
        when(postMapper.findActivePost(10L)).thenReturn(post(10L, 7L));
        when(postMapper.softDeletePost(10L, 7L)).thenReturn(1);

        service.deletePost(10L, 7L);

        verify(postMapper).softDeletePost(10L, 7L);
    }

    @Test
    void detailCalculatesMyPostOnServer() {
        when(postMapper.incrementViews(10L)).thenReturn(1);
        PostDetailDto detail = new PostDetailDto();
        detail.setContent("<p>본문</p>");
        when(postMapper.findPostDetail(10L)).thenReturn(detail);
        when(postMapper.findPostImages(10L)).thenReturn(List.of());
        when(postMapper.findActivePost(10L)).thenReturn(post(10L, 7L));

        assertThat(service.getPostDetail(10L, 7L).isMyPost()).isTrue();
    }

    private PostUpdateRequest request(String title, PostType postType, String content) {
        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle(title);
        request.setPostType(postType);
        request.setContent(content);
        return request;
    }

    private UserPost post(Long id, Long userId) {
        UserPost post = new UserPost();
        post.setId(id);
        post.setUserId(userId);
        return post;
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(expected));
    }
}
