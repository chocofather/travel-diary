package com.example.travlediary.controller.post;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.CommentLocationDto;
import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.post.PostCommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void guestCannotMutateCommentsOrLikes() throws Exception {
        mockMvc.perform(post("/post-comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":10,\"content\":\"댓글\"}"))
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
        when(service.create(10L, 7L, "댓글", null)).thenReturn(dto);
        when(service.update(30L, 7L, "수정")).thenReturn(dto);

        mockMvc.perform(post("/post-comments")
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":10,\"content\":\"댓글\"}"))
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
