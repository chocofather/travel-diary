package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.kto.KtoPhotoSearchItemResponse;
import com.example.travlediary.dto.kto.KtoPhotoSearchResponse;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.kto.KtoPhotoGalleryService;
import com.example.travlediary.service.kto.KtoPhotoApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminKtoPhotoController.class)
@Import(SecurityConfig.class)
class AdminKtoPhotoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KtoPhotoGalleryService ktoPhotoGalleryService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void adminSearchReceivesTheDedicatedPhotoJsonResponse() throws Exception {
        when(ktoPhotoGalleryService.search("경복궁", 1, 12)).thenReturn(response());

        mockMvc.perform(get("/admin/api/kto/photos/search")
                        .param("keyword", "  경복궁  ")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNo").value(1))
                .andExpect(jsonPath("$.numOfRows").value(12))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].externalContentId").value("123"))
                .andExpect(jsonPath("$.items[0].title").value("경복궁 야경"))
                .andExpect(jsonPath("$.items[0].sourceType").value("KTO_PHOTO_GALLERY"))
                .andExpect(jsonPath("$.items[0].licenseLabel").value("공공누리 제1유형"));

        verify(ktoPhotoGalleryService).search("경복궁", 1, 12);
    }

    @Test
    void invalidPaginationReturnsBadRequestWithoutCallingKto() throws Exception {
        mockMvc.perform(get("/admin/api/kto/photos/search")
                        .param("keyword", "경복궁")
                        .param("pageNo", "0")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/api/kto/photos/search")
                        .param("keyword", "경복궁")
                        .param("numOfRows", "51")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        verify(ktoPhotoGalleryService, never()).search(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void blankKeywordReturnsASafeJsonBadRequest() throws Exception {
        String responseBody = mockMvc.perform(get("/admin/api/kto/photos/search")
                        .param("keyword", "   ")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("검색어를 입력해 주세요."))
                .andReturn().getResponse().getContentAsString();

        assertSafeErrorBody(responseBody);
        verify(ktoPhotoGalleryService, never()).search(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void nonAdminCannotCallThePhotoSearchEndpoint() throws Exception {
        mockMvc.perform(get("/admin/api/kto/photos/search")
                        .param("keyword", "경복궁")
                        .with(user("member").roles("USER")))
                .andExpect(status().isForbidden());

        verify(ktoPhotoGalleryService, never()).search(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void upstreamFailuresUseASafeBadGatewayResponse() throws Exception {
        when(ktoPhotoGalleryService.search("경복궁", 1, 12))
                .thenThrow(KtoPhotoApiException.upstreamFailure());

        String responseBody = mockMvc.perform(get("/admin/api/kto/photos/search")
                        .param("keyword", "경복궁")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("관광사진 검색 서비스를 이용할 수 없습니다."))
                .andReturn().getResponse().getContentAsString();

        assertSafeErrorBody(responseBody);
    }

    @Test
    void missingApiKeyUsesASafeServiceUnavailableJsonResponse() throws Exception {
        when(ktoPhotoGalleryService.search("경복궁", 1, 12))
                .thenThrow(KtoPhotoApiException.missingApiKey());

        String responseBody = mockMvc.perform(get("/admin/api/kto/photos/search")
                        .param("keyword", "경복궁")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("관광사진 API 인증키가 설정되지 않았습니다."))
                .andReturn().getResponse().getContentAsString();

        assertSafeErrorBody(responseBody);
    }

    private void assertSafeErrorBody(String responseBody) {
        assertThat(responseBody).doesNotContain(
                "org.springframework",
                "com.example.travlediary",
                "ResponseStatusException",
                "stackTrace"
        );
    }

    private KtoPhotoSearchResponse response() {
        return new KtoPhotoSearchResponse(1, 12, 1, List.of(new KtoPhotoSearchItemResponse(
                "123", "경복궁 야경", "https://images.example.test/gyeongbokgung.jpg",
                "202501", "서울 종로구", "한국관광공사", "경복궁,궁궐",
                "20250101120000", "20250102120000", "KTO_PHOTO_GALLERY", "한국관광공사",
                "KOGL_TYPE_1", "공공누리 제1유형"
        )));
    }
}
