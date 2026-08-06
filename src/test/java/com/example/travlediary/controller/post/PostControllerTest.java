package com.example.travlediary.controller.post;

import com.example.travlediary.dto.PostUpdateRequest;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.post.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    @Mock
    private PostService postService;
    @Mock
    private FileUploadService fileUploadService;
    @Mock
    private CustomUserDetails userDetails;

    private PostController controller;

    @BeforeEach
    void setUp() {
        controller = new PostController(postService, fileUploadService);
        when(userDetails.getId()).thenReturn(7L);
    }

    @Test
    void updateUsesAuthenticatedUserAndRedirectsToDetail() {
        PostUpdateRequest request = new PostUpdateRequest();

        String view = controller.updatePost(10L, request, userDetails);

        verify(postService).updatePost(10L, 7L, request);
        assertThat(view).isEqualTo("redirect:/post/10");
    }

    @Test
    void deleteUsesAuthenticatedUserAndRedirectsToBoard() {
        String view = controller.deletePost(10L, userDetails);

        verify(postService).deletePost(10L, 7L);
        assertThat(view).isEqualTo("redirect:/board/list?boardType=post");
    }
}
