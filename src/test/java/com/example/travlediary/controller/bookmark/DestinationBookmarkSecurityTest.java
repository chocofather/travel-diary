package com.example.travlediary.controller.bookmark;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.controller.destination.DestinationBookmarkController;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.destination.DestinationBookmarkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DestinationBookmarkController.class)
@Import(SecurityConfig.class)
class DestinationBookmarkSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DestinationBookmarkService service;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private CustomUserDetails userDetails;

    @Test
    void destinationDeleteRequiresAuthenticationAndCsrfThenUsesPrincipal() throws Exception {
        mockMvc.perform(delete("/bookmarks/destinations/10")
                        .with(csrf()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        when(userDetails.getId()).thenReturn(7L);
        var authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, List.of());

        mockMvc.perform(delete("/bookmarks/destinations/10")
                        .with(authentication(authentication)))
                .andExpect(status().isForbidden());
        verify(service, never()).removeBookmark(7L, 10L);

        mockMvc.perform(delete("/bookmarks/destinations/10")
                        .with(authentication(authentication)).with(csrf()))
                .andExpect(status().isNoContent());
        verify(service).removeBookmark(7L, 10L);
    }

    @Test
    void existingDestinationToggleAlsoRequiresCsrfAndStillUsesTheAuthenticatedUser() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, List.of());
        when(userDetails.getId()).thenReturn(7L);
        when(service.toggleBookmark(7L, 10L)).thenReturn(true);

        mockMvc.perform(post("/bookmarks")
                        .param("destinationId", "10")
                        .with(authentication(authentication)))
                .andExpect(status().isForbidden());
        verify(service, never()).toggleBookmark(7L, 10L);

        mockMvc.perform(post("/bookmarks")
                        .param("destinationId", "10")
                        .with(authentication(authentication)).with(csrf()))
                .andExpect(status().isOk());
        verify(service).toggleBookmark(7L, 10L);
    }

    @Test
    void destinationBookmarkCheckUsesAuthenticatedPrincipalId() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, List.of());
        when(userDetails.getId()).thenReturn(7L);
        when(service.isBookmarked(7L, 10L)).thenReturn(true);

        mockMvc.perform(get("/bookmarks/check")
                        .param("destinationId", "10")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(service).isBookmarked(7L, 10L);
    }

    @Test
    void anonymousDestinationBookmarkCheckReturnsFalse() throws Exception {
        mockMvc.perform(get("/bookmarks/check").param("destinationId", "10"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));

        verify(service, never()).isBookmarked(7L, 10L);
    }
}
