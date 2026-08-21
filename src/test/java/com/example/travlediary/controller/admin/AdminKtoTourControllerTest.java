package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.kto.KtoTourAutofillResponse;
import com.example.travlediary.dto.kto.KtoTourRegionMatchResponse;
import com.example.travlediary.dto.kto.KtoTourSearchItemResponse;
import com.example.travlediary.dto.kto.KtoTourSearchResponse;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.kto.KtoTourApiException;
import com.example.travlediary.service.kto.KtoTourRegionMatchService;
import com.example.travlediary.service.kto.KtoTourService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminKtoTourController.class)
@Import(SecurityConfig.class)
class AdminKtoTourControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private KtoTourService ktoTourService;
    @MockitoBean
    private KtoTourRegionMatchService ktoTourRegionMatchService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void adminCanSearchAndLoadADetail() throws Exception {
        when(ktoTourService.search("창덕궁", 1, 10)).thenReturn(new KtoTourSearchResponse(
                1, 10, 1, List.of(new KtoTourSearchItemResponse(
                "126508", "12", "관광지", "창덕궁", "서울 종로구", "126.991", "37.579"))));
        when(ktoTourService.getDetail("126508", "12")).thenReturn(new KtoTourAutofillResponse(
                "126508", "12", "창덕궁", "서울 종로구", "126.991", "37.579",
                "궁궐 설명", "https://example.test", "02-0000-0000", "월요일",
                "09:00~18:00", "무료", "안내"));
        when(ktoTourRegionMatchService.match("서울 종로구")).thenReturn(
                KtoTourRegionMatchResponse.matched(List.of(
                        new KtoTourRegionMatchResponse.RegionPathItem(7L, "대한민국"),
                        new KtoTourRegionMatchResponse.RegionPathItem(38L, "서울"),
                        new KtoTourRegionMatchResponse.RegionPathItem(235L, "종로구")
                )));

        mockMvc.perform(get("/admin/api/kto/tour/search")
                        .param("keyword", " 창덕궁 ")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].contentId").value("126508"))
                .andExpect(jsonPath("$.items[0].contentTypeName").value("관광지"));
        mockMvc.perform(get("/admin/api/kto/tour/detail")
                        .param("contentId", "126508")
                        .param("contentTypeId", "12")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("창덕궁"))
                .andExpect(jsonPath("$.longitude").value("126.991"))
                .andExpect(jsonPath("$.latitude").value("37.579"))
                .andExpect(jsonPath("$.regionMatch.matched").value(true))
                .andExpect(jsonPath("$.regionMatch.path[0].id").value(7))
                .andExpect(jsonPath("$.regionMatch.path[2].id").value(235))
                .andExpect(jsonPath("$.regionMatch.deepestRegionId").value(235));

        verify(ktoTourService).search("창덕궁", 1, 10);
        verify(ktoTourService).getDetail("126508", "12");
        verify(ktoTourRegionMatchService).match("서울 종로구");
    }

    @Test
    void regionMatchingFailureDoesNotFailTheTourDetail() throws Exception {
        when(ktoTourService.getDetail("126508", "12")).thenReturn(new KtoTourAutofillResponse(
                "126508", "12", "창덕궁", "잘못된 주소", "126.991", "37.579",
                "궁궐 설명", null, null, null, null, null, null));
        when(ktoTourRegionMatchService.match("잘못된 주소")).thenThrow(new IllegalStateException("region failure"));

        mockMvc.perform(get("/admin/api/kto/tour/detail")
                        .param("contentId", "126508")
                        .param("contentTypeId", "12")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("창덕궁"))
                .andExpect(jsonPath("$.regionMatch.matched").value(false))
                .andExpect(jsonPath("$.regionMatch.path").isEmpty())
                .andExpect(jsonPath("$.regionMatch.deepestRegionId").doesNotExist());
    }

    @Test
    void invalidRequestsReturnSafeBadRequestWithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/admin/api/kto/tour/search")
                        .param("keyword", "   ")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("검색어를 입력해 주세요."));
        mockMvc.perform(get("/admin/api/kto/tour/search")
                        .param("keyword", "창덕궁").param("pageNo", "0")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/api/kto/tour/detail")
                        .param("contentId", "").param("contentTypeId", "12")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        verify(ktoTourService, never()).search(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(ktoTourService, never()).getDetail(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void configurationAndUpstreamFailuresUseSafeJsonStatuses() throws Exception {
        when(ktoTourService.search("창덕궁", 1, 10)).thenThrow(KtoTourApiException.missingApiKey());
        mockMvc.perform(get("/admin/api/kto/tour/search")
                        .param("keyword", "창덕궁")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("TourAPI 인증키가 설정되지 않았습니다."));

        when(ktoTourService.getDetail("126508", "12")).thenThrow(KtoTourApiException.upstreamFailure());
        String body = mockMvc.perform(get("/admin/api/kto/tour/detail")
                        .param("contentId", "126508").param("contentTypeId", "12")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("관광정보를 불러오지 못했습니다."))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(
                "org.springframework", "com.example.travlediary", "stackTrace", "serviceKey");
    }
}
