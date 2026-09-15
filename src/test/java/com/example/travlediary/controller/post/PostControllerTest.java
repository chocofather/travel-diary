package com.example.travlediary.controller.post;

import com.example.travlediary.dto.PostDetailDto;
import com.example.travlediary.dto.PostUpdateRequest;
import com.example.travlediary.model.PostImage;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.post.PostService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;

import java.sql.Timestamp;
import java.util.List;

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

    @Test
    void publicPostDetailAddsArticleJsonLdWithAuthorAndDates() throws Exception {
        PostDetailDto post = new PostDetailDto();
        post.setId(10L);
        post.setTitle("제주 버스 여행 팁");
        post.setContent("<p>환승 시간을 확인하세요.</p>");
        post.setNickname("여행자민준");
        post.setCreatedAt(Timestamp.valueOf("2026-09-01 10:00:00"));
        post.setUpdatedAt(Timestamp.valueOf("2026-09-02 11:00:00"));
        PostImage image = new PostImage();
        image.setImageUrl("/uploads/posts/jeju.webp");
        post.setImages(List.of(image));
        when(postService.getPostDetail(10L, 7L)).thenReturn(post);
        ConcurrentModel model = new ConcurrentModel();
        model.addAttribute("seoSiteBaseUrl", "https://travel.example");

        controller.postDetail(10L, userDetails, model);

        JsonNode article = new ObjectMapper().readTree((String) model.getAttribute("seoJsonLd"))
                .path("@graph").get(0);
        assertThat(article.path("@type").asText()).isEqualTo("Article");
        assertThat(article.path("headline").asText()).isEqualTo("제주 버스 여행 팁");
        assertThat(article.path("author").path("name").asText()).isEqualTo("여행자민준");
        assertThat(article.path("datePublished").asText()).isNotBlank();
        assertThat(article.path("dateModified").asText()).isNotBlank();
        assertThat(article.path("url").asText()).isEqualTo("https://travel.example/post/10");
    }
}
