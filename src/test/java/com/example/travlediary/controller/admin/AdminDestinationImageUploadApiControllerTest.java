package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.destination.DestinationImageService;
import com.example.travlediary.service.destination.DestinationImageUploadReceipts;
import com.example.travlediary.service.file.UnsupportedImageFormatException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 이미지 관리 화면의 나눠 올리기: 사진 한 장씩 받아 기존 저장 서비스로 저장한다. */
@WebMvcTest(AdminDestinationImageUploadApiController.class)
@Import({SecurityConfig.class, DestinationImageUploadReceipts.class})
class AdminDestinationImageUploadApiControllerTest {

    private static final String URL = "/admin/api/destinations/10/images";
    private static final String KEY = "0f8d2c1e-5b7a-4e2f-9c3d-1a2b3c4d5e6f";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DestinationImageService destinationImageService;
    @MockitoBean private com.example.travlediary.service.file.DestinationCardThumbnailService cardThumbnailService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void onePhotoIsSavedWithItsSourceAndResendingTheSameUploadDoesNotSaveItTwice() throws Exception {
        when(destinationImageService.saveUploadedImage(eq(10L), any(), eq("한국관광공사"), any(), any(), any(),
                any(), any(), any())).thenReturn(321L);

        mockMvc.perform(upload(KEY).param("sourceName", "한국관광공사"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageId").value(321))
                .andExpect(jsonPath("$.alreadySaved").value(false));
        // 저장 뒤 응답이 끊겨 화면이 같은 사진을 다시 보낸 경우
        mockMvc.perform(upload(KEY).param("sourceName", "한국관광공사"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageId").value(321))
                .andExpect(jsonPath("$.alreadySaved").value(true));

        verify(destinationImageService, times(1)).saveUploadedImage(eq(10L), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void aRejectedPhotoReturnsItsReasonAndCanBeSentAgain() throws Exception {
        when(destinationImageService.saveUploadedImage(eq(10L), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new UnsupportedImageFormatException("JPG, JPEG, PNG 이미지 파일만 업로드할 수 있습니다."))
                .thenReturn(55L);

        mockMvc.perform(upload("retry-key-0001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("JPG, JPEG, PNG 이미지 파일만 업로드할 수 있습니다."));
        // 실패한 사진은 영수증이 남지 않으므로 같은 키로 다시 보내면 저장을 다시 시도한다.
        mockMvc.perform(upload("retry-key-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageId").value(55));
    }

    @Test
    void onlyAdministratorsWithTheCsrfTokenCanUpload() throws Exception {
        mockMvc.perform(multipart(URL).file(photo()).with(user("user").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(URL).file(photo()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verify(destinationImageService, never()).saveUploadedImage(any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    private MockMultipartHttpServletRequestBuilder upload(String key) {
        return (MockMultipartHttpServletRequestBuilder) multipart(URL).file(photo())
                .param("uploadKey", key)
                .with(user("admin").roles("ADMIN")).with(csrf());
    }

    private static MockMultipartFile photo() {
        return new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1});
    }
}
