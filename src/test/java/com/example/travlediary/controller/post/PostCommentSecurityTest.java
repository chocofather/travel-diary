package com.example.travlediary.controller.post;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.dto.CommentLocationDto;
import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.post.PostCommentService;
import com.example.travlediary.service.translation.ContentTranslationResponse;
import com.example.travlediary.service.translation.ContentTranslationService;
import com.example.travlediary.service.translation.TranslatableContentType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostCommentController.class)
@Import(SecurityConfig.class)
class PostCommentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostCommentService service;
    @MockitoBean
    private ContentTranslationService contentTranslationService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private CustomUserDetails userDetails;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void guestCanGetComments() throws Exception {
        when(service.getComments(10L, null)).thenReturn(List.of());

        mockMvc.perform(get("/post-comments").param("postId", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void guestCanGetPagedComments() throws Exception {
        when(service.getCommentsPage(10L, null, 0, 5, "latest"))
                .thenReturn(new PageResult<>(List.of(), 0, 0, 5, 0));

        mockMvc.perform(get("/post-comments/page")
                        .param("postId", "10")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "latest"))
                .andExpect(status().isOk());

        verify(service).getCommentsPage(10L, null, 0, 5, "latest");
    }

    @Test
    void guestCanResolveAValidatedCommentLocation() throws Exception {
        when(service.getCommentLocation(10L, 35L))
                .thenReturn(Optional.of(new CommentLocationDto(2)));

        mockMvc.perform(get("/post-comments/35/location").param("postId", "10"))
                .andExpect(status().isOk());

        verify(service).getCommentLocation(10L, 35L);
    }

    @Test
    void guestCanTranslateUsingServerLocaleAndRemoteAddress() throws Exception {
        when(contentTranslationService.translate(
                TranslatableContentType.POST_COMMENT, 35L, "ja", "203.0.113.9", null))
                .thenReturn(ContentTranslationResponse.ready(
                        "美しい場所です。", "ko", "ja", false));

        mockMvc.perform(get("/post-comments/35/translation")
                        .cookie(new Cookie("TRAVEL_DIARY_LOCALE", "ja"))
                        .header("X-Forwarded-For", "198.51.100.77")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        }))
                .andExpect(status().isOk());

        verify(contentTranslationService).translate(
                TranslatableContentType.POST_COMMENT, 35L, "ja", "203.0.113.9", null);
    }

    @Test
    void authenticatedTranslationUsesImmutableUserId() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setUsername("changeable-login-id");
        user.setUserPassword("encoded");
        user.setUserRole(UserRole.USER);
        CustomUserDetails principal = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        when(contentTranslationService.translate(
                TranslatableContentType.POST_COMMENT, 35L, "ko", "203.0.113.9", 42L))
                .thenReturn(ContentTranslationResponse.ready("번역", "en", "ko", false));

        mockMvc.perform(get("/post-comments/35/translation")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        })
                        .with(authentication(authentication)))
                .andExpect(status().isOk());

        verify(contentTranslationService).translate(
                TranslatableContentType.POST_COMMENT, 35L, "ko", "203.0.113.9", 42L);
    }

    @Test
    void guestCannotMutateCommentsOrLikes() throws Exception {
        mockMvc.perform(multipart("/post-comments")
                        .param("postId", "10")
                        .param("content", "댓글")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/post-comments/30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/post-comments/30").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/post-comments/30/likes").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/post-comments/30/likes").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanMutateCommentsAndLikes() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        PostCommentDto dto = new PostCommentDto();
        when(service.create(eq(10L), eq(7L), eq("댓글"), isNull(), any())).thenReturn(dto);
        when(service.update(30L, 7L, "수정")).thenReturn(dto);

        mockMvc.perform(multipart("/post-comments")
                        .file(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1}))
                        .param("postId", "10")
                        .param("content", "댓글")
                        .with(authentication(authentication)))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/post-comments/30")
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/post-comments/30").with(authentication(authentication)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/post-comments/30/likes").with(authentication(authentication)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/post-comments/30/likes").with(authentication(authentication)))
                .andExpect(status().isNoContent());

        verify(service).likeComment(30L, 7L);
        verify(service).unlikeComment(30L, 7L);
    }
}
