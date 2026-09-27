package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.destination.DestinationImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 순서 편집의 '순서 저장': 사진 번호 목록을 한 번에 받아 저장한다. */
@WebMvcTest(AdminDestinationImageOrderApiController.class)
@Import(SecurityConfig.class)
class AdminDestinationImageOrderApiControllerTest {

    private static final String URL = "/admin/api/destinations/10/images/order";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DestinationImageService destinationImageService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void theWholeOrderIsSavedAtOnceAndAMismatchedListExplainsWhy() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"imageIds\":[3,1,2]}")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(3));
        verify(destinationImageService).saveImageOrder(10L, List.of(3L, 1L, 2L));

        doThrow(new DestinationImageService.InvalidImageOrderException(
                "이 여행지의 사진 목록이 바뀌었습니다. 새로고침한 뒤 다시 순서를 정해 주세요."))
                .when(destinationImageService).saveImageOrder(10L, List.of(1L, 99L));
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"imageIds\":[1,99]}")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이 여행지의 사진 목록이 바뀌었습니다. 새로고침한 뒤 다시 순서를 정해 주세요."));
    }

    @Test
    void onlyAdministratorsWithTheCsrfTokenCanReorder() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"imageIds\":[1]}")
                        .with(user("user").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"imageIds\":[1]}")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verify(destinationImageService, never()).saveImageOrder(any(), any());
    }
}
