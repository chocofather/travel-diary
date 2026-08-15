package com.example.travlediary.controller.destination;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.dto.CommentLocationDto;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.comment.CommentLikeService;
import com.example.travlediary.service.comment.DestinationCommentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    @Test
    void everySelectedImagePartReachesTheService() throws Exception {
        User user = new User();
        user.setId(7L);
        user.setUsername("writer");
        user.setUserPassword("encoded");
        user.setUserRole(UserRole.USER);
        CustomUserDetails principal = new CustomUserDetails(user);

        mockMvc.perform(multipart("/comments")
                        .file(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1}))
                        .file(new MockMultipartFile("images", "b.jpg", "image/jpeg", new byte[]{2}))
                        .file(new MockMultipartFile("images", "c.jpg", "image/jpeg", new byte[]{3}))
                        .param("destinationId", "10")
                        .param("content", "사진 3장")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, "n/a", principal.getAuthorities()))))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MultipartFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(destinationCommentService)
                .create(eq(10L), eq(7L), eq("사진 3장"), captor.capture(), isNull());
        assertThat(captor.getValue())
                .extracting(MultipartFile::getOriginalFilename)
                .containsExactly("a.jpg", "b.jpg", "c.jpg");
    }

    @Test
    void guestCanResolveAValidatedCommentLocation() throws Exception {
        when(destinationCommentService.getCommentLocation(10L, 35L))
                .thenReturn(Optional.of(new CommentLocationDto(2)));

        mockMvc.perform(get("/comments/35/location")
                        .param("destinationId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));

        verify(destinationCommentService).getCommentLocation(10L, 35L);
    }
}
