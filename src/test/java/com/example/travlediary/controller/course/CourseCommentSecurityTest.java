package com.example.travlediary.controller.course;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.course.CourseCommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseCommentController.class)
@Import(SecurityConfig.class)
class CourseCommentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseCommentService service;
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

        mockMvc.perform(get("/course-comments").param("courseId", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void guestCanGetPagedComments() throws Exception {
        when(service.getCommentsPage(10L, null, 0, 5, "latest"))
                .thenReturn(new PageResult<>(List.of(), 0, 0, 5, 0));

        mockMvc.perform(get("/course-comments/page")
                        .param("courseId", "10")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "latest"))
                .andExpect(status().isOk());

        verify(service).getCommentsPage(10L, null, 0, 5, "latest");
    }

    @Test
    void guestCannotMutateComments() throws Exception {
        mockMvc.perform(post("/course-comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":10,\"replyToCommentId\":20,\"content\":\"댓글\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/course-comments/30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/course-comments/30").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/course-comments/30/likes").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/course-comments/30/likes").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanMutateComments() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        CourseCommentDto dto = new CourseCommentDto();
        when(service.create(10L, 7L, "댓글", 20L)).thenReturn(dto);
        when(service.update(30L, 7L, "수정")).thenReturn(dto);

        mockMvc.perform(post("/course-comments")
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":10,\"replyToCommentId\":20,\"content\":\"댓글\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/course-comments/30")
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/course-comments/30").with(authentication(authentication)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/course-comments/30/likes").with(authentication(authentication)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/course-comments/30/likes").with(authentication(authentication)))
                .andExpect(status().isNoContent());

        verify(service).likeComment(30L, 7L);
        verify(service).unlikeComment(30L, 7L);
    }
}
