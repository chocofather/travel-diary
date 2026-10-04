package com.tripbora.controller.bookmark;

import com.tripbora.config.CustomLoginSuccessHandler;
import com.tripbora.config.CustomLogoutSuccessHandler;
import com.tripbora.config.SecurityConfig;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.security.CustomUserDetails;
import com.tripbora.service.bookmark.ContentBookmarkService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
        mockMvc.perform(post("/bookmarks/posts/10").with(csrf()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/bookmarks/posts/10").with(csrf()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/bookmarks/courses/20").with(csrf()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/bookmarks/courses/20").with(csrf()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanChangeContentBookmarks() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());

        mockMvc.perform(post("/bookmarks/posts/10").with(authentication(authentication)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/bookmarks/posts/10").with(authentication(authentication)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/bookmarks/courses/20").with(authentication(authentication)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/bookmarks/courses/20").with(authentication(authentication)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).bookmarkPost(10L, 7L);
        verify(service).unbookmarkPost(10L, 7L);
        verify(service).bookmarkCourse(20L, 7L);
        verify(service).unbookmarkCourse(20L, 7L);
    }

    @Test
    void guestCannotChangeTravelInfoBookmarks() throws Exception {
        mockMvc.perform(post("/bookmarks/travel-info/30")
                        .with(csrf()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/bookmarks/travel-info/30")
                        .with(csrf()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void travelInfoBookmarkRequiresCsrfOnlyForAuthenticatedMutation() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());

        mockMvc.perform(post("/bookmarks/travel-info/30")
                        .with(authentication(authentication)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/bookmarks/travel-info/30")
                        .with(authentication(authentication)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/bookmarks/travel-info/30")
                        .with(authentication(authentication)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/bookmarks/travel-info/30")
                        .with(authentication(authentication)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).bookmarkTravelInfo(30L, 7L);
        verify(service).unbookmarkTravelInfo(30L, 7L);
    }

    @Test
    void postAndCourseBookmarkEndpointsRequireCsrf() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());

        mockMvc.perform(post("/bookmarks/posts/10").with(authentication(authentication)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/bookmarks/courses/20").with(authentication(authentication)))
                .andExpect(status().isForbidden());

        verify(service, org.mockito.Mockito.never()).bookmarkPost(10L, 7L);
        verify(service, org.mockito.Mockito.never()).unbookmarkCourse(20L, 7L);
    }
}
