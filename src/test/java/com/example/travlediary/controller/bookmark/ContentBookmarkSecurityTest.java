package com.example.travlediary.controller.bookmark;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.bookmark.ContentBookmarkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentBookmarkController.class)
@Import(SecurityConfig.class)
class ContentBookmarkSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContentBookmarkService service;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private CustomUserDetails userDetails;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void guestCannotChangeContentBookmarks() throws Exception {
        mockMvc.perform(post("/bookmarks/posts/10").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/bookmarks/posts/10").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/bookmarks/courses/20").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/bookmarks/courses/20").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanChangeContentBookmarks() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());

        mockMvc.perform(post("/bookmarks/posts/10").with(authentication(authentication)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/bookmarks/posts/10").with(authentication(authentication)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/bookmarks/courses/20").with(authentication(authentication)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/bookmarks/courses/20").with(authentication(authentication)))
                .andExpect(status().isNoContent());

        verify(service).bookmarkPost(10L, 7L);
        verify(service).unbookmarkPost(10L, 7L);
        verify(service).bookmarkCourse(20L, 7L);
        verify(service).unbookmarkCourse(20L, 7L);
    }
}
