package com.example.travlediary.controller.post;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.translation.UserPostTranslationResponse;
import com.example.travlediary.service.translation.UserPostTranslationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserPostTranslationController.class)
@Import(SecurityConfig.class)
class UserPostTranslationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserPostTranslationService service;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void guestCanTranslateAVisibleUserPost() throws Exception {
        when(service.translate(eq(9L), eq("ko"), any(), eq(null)))
                .thenReturn(UserPostTranslationResponse.ready(
                        "번역 제목", "<p>번역 본문</p>", true));

        mockMvc.perform(get("/post/9/translation"))
                .andExpect(status().isOk());
    }
}
