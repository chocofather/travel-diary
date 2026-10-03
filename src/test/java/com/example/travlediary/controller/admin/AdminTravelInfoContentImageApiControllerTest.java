package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.file.UnsupportedImageFormatException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 구조화 콘텐츠 본문 이미지 업로드: 관리자만, CSRF 그대로, 응답은 url / width / height. */
@WebMvcTest(AdminTravelInfoContentImageApiController.class)
@Import(SecurityConfig.class)
class AdminTravelInfoContentImageApiControllerTest {

    private static final String ENDPOINT = "/admin/api/travel-info/content-images";
    private static final String URL =
            "/uploads/travel-info/content/123e4567-e89b-42d3-a456-426614174000.jpg";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FileUploadService fileUploadService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void adminUploadReturnsTheStoredUrlAndServerMeasuredSize() throws Exception {
        when(fileUploadService.saveTravelInfoContentImage(any()))
                .thenReturn(new FileUploadService.StoredContentImage(URL, 2000, 1333));

        mockMvc.perform(multipart(ENDPOINT).file(image())
                        // 화면이 크기를 보내도 쓰지 않는다.
                        .param("width", "1").param("height", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(URL))
                .andExpect(jsonPath("$.width").value(2000))
                .andExpect(jsonPath("$.height").value(1333));
    }

    @Test
    void rejectedImageReturnsItsReasonAsJson() throws Exception {
        when(fileUploadService.saveTravelInfoContentImage(any()))
                .thenThrow(new UnsupportedImageFormatException(FileUploadService.UNSUPPORTED_IMAGE_MESSAGE));

        mockMvc.perform(multipart(ENDPOINT).file(image())
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(FileUploadService.UNSUPPORTED_IMAGE_MESSAGE));
    }

    @Test
    void storageFailureReturnsAGenericJsonError() throws Exception {
        when(fileUploadService.saveTravelInfoContentImage(any()))
                .thenThrow(new RuntimeException("disk full /var/uploads"));

        mockMvc.perform(multipart(ENDPOINT).file(image())
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("이미지를 저장하지 못했습니다. 잠시 후 다시 올려 주세요."));
    }

    @Test
    void onlyAdminsWithCsrfTokenCanUpload() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(image()).with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(ENDPOINT).file(image()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(ENDPOINT).file(image()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(fileUploadService, never()).saveTravelInfoContentImage(any());
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8});
    }
}
