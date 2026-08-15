package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.ContentModerationForm;
import com.example.travlediary.model.ModerationTargetType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.moderation.ContentModerationService;
import com.example.travlediary.service.moderation.ModerationValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminContentModerationController.class)
@Import(SecurityConfig.class)
class AdminContentModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContentModerationService contentModerationService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void hidePassesTargetReasonAndAdminIdThenReturnsToTheContentPage() throws Exception {
        mockMvc.perform(post("/admin/contents/POST/3/hide")
                        .with(user(admin())).with(csrf())
                        .param("reason", "욕설")
                        .param("adminNote", "내부 메모")
                        .param("redirect", "/board/list?boardType=post"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/board/list?boardType=post"));

        ArgumentCaptor<ContentModerationForm> captor =
                ArgumentCaptor.forClass(ContentModerationForm.class);
        verify(contentModerationService).hide(eq(ModerationTargetType.POST), eq(3L),
                captor.capture(), eq(1L));
        assertThat(captor.getValue().getReason()).isEqualTo("욕설");
        assertThat(captor.getValue().getAdminNote()).isEqualTo("내부 메모");
    }

    @Test
    void everySupportedTargetTypeHasAnEndpoint() throws Exception {
        for (ModerationTargetType type : ModerationTargetType.values()) {
            mockMvc.perform(post("/admin/contents/" + type.name() + "/3/hide")
                            .with(user(admin())).with(csrf())
                            .param("reason", "사유"))
                    .andExpect(status().is3xxRedirection());
            verify(contentModerationService).hide(eq(type), eq(3L), any(), eq(1L));
        }
    }

    @Test
    void restoreCallsTheServiceWithTheRestoreReason() throws Exception {
        mockMvc.perform(post("/admin/contents/POST_COMMENT/8/restore")
                        .with(user(admin())).with(csrf())
                        .param("reason", "오탐")
                        .param("redirect", "/post/3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/post/3"));

        verify(contentModerationService).restore(eq(ModerationTargetType.POST_COMMENT), eq(8L),
                any(), eq(1L));
    }

    @Test
    void unknownTargetTypeIsNotFound() throws Exception {
        mockMvc.perform(post("/admin/contents/UNKNOWN/3/hide")
                        .with(user(admin())).with(csrf())
                        .param("reason", "사유"))
                .andExpect(status().isNotFound());

        verify(contentModerationService, never()).hide(any(), anyLong(), any(), anyLong());
    }

    @Test
    void externalRedirectTargetsFallBackToHome() throws Exception {
        mockMvc.perform(post("/admin/contents/POST/3/hide")
                        .with(user(admin())).with(csrf())
                        .param("reason", "사유")
                        .param("redirect", "https://evil.example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void rejectedModerationReturnsBadRequest() throws Exception {
        doThrow(new ModerationValidationException(null, "이미 조치 중인 콘텐츠입니다."))
                .when(contentModerationService).hide(eq(ModerationTargetType.POST), eq(3L),
                        any(), eq(1L));

        mockMvc.perform(post("/admin/contents/POST/3/hide")
                        .with(user(admin())).with(csrf())
                        .param("reason", "사유"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moderationEndpointsRequireCsrfAndAdminRole() throws Exception {
        mockMvc.perform(post("/admin/contents/POST/3/hide")
                        .with(user(admin()))
                        .param("reason", "사유"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/contents/POST/3/hide")
                        .with(user("member").roles("USER")).with(csrf())
                        .param("reason", "사유"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/contents/POST/3/restore")
                        .with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());

        verify(contentModerationService, never()).hide(any(), anyLong(), any(), anyLong());
        verify(contentModerationService, never()).restore(any(), anyLong(), any(), anyLong());
    }

    private CustomUserDetails admin() {
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("master");
        adminUser.setUserPassword("password");
        adminUser.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(adminUser);
    }
}
