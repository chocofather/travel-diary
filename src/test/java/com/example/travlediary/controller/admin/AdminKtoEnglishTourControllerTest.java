package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.kto.KtoEnglishTourCandidateResponse;
import com.example.travlediary.dto.kto.KtoEnglishTourDetailResponse;
import com.example.travlediary.dto.kto.KtoEnglishTourMatchResponse;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.kto.KtoEnglishTourApiException;
import com.example.travlediary.service.kto.KtoEnglishTourService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminKtoEnglishTourController.class)
@Import(SecurityConfig.class)
class AdminKtoEnglishTourControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private KtoEnglishTourService ktoEnglishTourService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void adminCanMatchByLocationAndLoadEnglishDetail() throws Exception {
        KtoEnglishTourCandidateResponse candidate = new KtoEnglishTourCandidateResponse(
                "eng-1", "76", "Changdeokgung Palace", "126.991", "37.579", 0.0);
        when(ktoEnglishTourService.match("창덕궁", "126.991", "37.579")).thenReturn(
                new KtoEnglishTourMatchResponse(KtoEnglishTourMatchResponse.Status.MATCHED,
                        candidate, List.of()));
        when(ktoEnglishTourService.getDetail("eng-1", "76")).thenReturn(
                new KtoEnglishTourDetailResponse("Changdeokgung Palace", "English overview",
                        null, null, null));

        mockMvc.perform(get("/admin/api/kto/tour/english-match")
                        .param("title", "창덕궁")
                        .param("mapX", "126.991").param("mapY", "37.579")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.matched.contentId").value("eng-1"));
        mockMvc.perform(get("/admin/api/kto/tour/english-detail")
                        .param("contentId", "eng-1")
                        .param("contentTypeId", "76")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Changdeokgung Palace"))
                .andExpect(jsonPath("$.overview").value("English overview"));

        verify(ktoEnglishTourService).match("창덕궁", "126.991", "37.579");
        // 매칭으로 받은 영문 유형 코드를 그대로 넘긴다 (국문 코드와 다르다)
        verify(ktoEnglishTourService).getDetail("eng-1", "76");
    }

    @Test
    void invalidCoordinatesAndContentIdReturnBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(get("/admin/api/kto/tour/english-match")
                        .param("title", "창덕궁").param("mapX", "").param("mapY", "37.579")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("좌표가 올바르지 않습니다."));
        mockMvc.perform(get("/admin/api/kto/tour/english-match")
                        .param("title", "창덕궁").param("mapX", "200").param("mapY", "37.579")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/api/kto/tour/english-match")
                        .param("title", " ").param("mapX", "126.991").param("mapY", "37.579")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/api/kto/tour/english-detail")
                        .param("contentId", " ")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        verify(ktoEnglishTourService, never()).match(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(ktoEnglishTourService, never()).getDetail(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void englishFailuresUseSafeJsonWithoutAffectingKoreanEndpointContract() throws Exception {
        when(ktoEnglishTourService.match("경복궁", "126.991", "37.579"))
                .thenThrow(KtoEnglishTourApiException.missingApiKey());
        mockMvc.perform(get("/admin/api/kto/tour/english-match")
                        .param("title", "경복궁")
                        .param("mapX", "126.991").param("mapY", "37.579")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("영문 TourAPI 인증키가 설정되지 않았습니다."));

        when(ktoEnglishTourService.getDetail("eng-1", ""))
                .thenThrow(KtoEnglishTourApiException.upstreamFailure());
        String body = mockMvc.perform(get("/admin/api/kto/tour/english-detail")
                        .param("contentId", "eng-1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("영문 관광정보를 불러오지 못했습니다."))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(
                "org.springframework", "com.example.travlediary", "stackTrace", "serviceKey");
    }
}
