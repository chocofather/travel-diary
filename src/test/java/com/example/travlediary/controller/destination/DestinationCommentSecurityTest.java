package com.example.travlediary.controller.destination;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.comment.CommentLikeService;
import com.example.travlediary.service.comment.DestinationCommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DestinationCommentController.class)
@Import(SecurityConfig.class)
class DestinationCommentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DestinationCommentService destinationCommentService;
    @MockitoBean
    private CommentLikeService commentLikeService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void guestCanGetLatestPagedCommentsWithThreadAndActiveCommentTotals() throws Exception {
        when(destinationCommentService.getCommentsPaged(10L, null, 0, 5, "latest"))
                .thenReturn(new PageResult<>(List.of(), 6, 0, 5, 9));

        mockMvc.perform(get("/comments/list/page")
                        .param("destinationId", "10")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalCommentCount").value(9))
                .andExpect(jsonPath("$.last").value(false));

        verify(destinationCommentService).getCommentsPaged(10L, null, 0, 5, "latest");
    }
}
