package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.kto.KtoFestivalAutofillResponse;
import com.example.travlediary.dto.kto.KtoFestivalSearchItemResponse;
import com.example.travlediary.dto.kto.KtoFestivalSearchResponse;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.kto.KtoFestivalService;
import com.example.travlediary.service.kto.KtoTourApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminKtoFestivalController.class)
@Import(SecurityConfig.class)
class AdminKtoFestivalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KtoFestivalService ktoFestivalService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void adminCanSearchFestivalsForADateRange() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 9, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 30);
        when(ktoFestivalService.search(startDate, endDate, 1, 10)).thenReturn(new KtoFestivalSearchResponse(
                1, 10, 1, List.of(new KtoFestivalSearchItemResponse(
                "12345", "서울 축제", startDate, LocalDate.of(2026, 9, 3),
                "https://images.example.test/main.jpg", "https://images.example.test/thumb.jpg",
                "서울 종로구", "EV", "EV01", "EV010100", "축제"
        ))));

        mockMvc.perform(get("/admin/api/kto/festivals/search")
                        .param("eventStartDate", "2026-09-01")
                        .param("eventEndDate", "2026-09-30")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNo").value(1))
                .andExpect(jsonPath("$.items[0].contentId").value("12345"))
                .andExpect(jsonPath("$.items[0].eventStartDate").value("2026-09-01"))
                .andExpect(jsonPath("$.items[0].categoryName").value("축제"));

        verify(ktoFestivalService).search(startDate, endDate, 1, 10);
    }

    @Test
    void adminCanSearchFestivalsByKeywordWithoutDates() throws Exception {
        when(ktoFestivalService.searchByKeyword("경복궁", 1, 10)).thenReturn(new KtoFestivalSearchResponse(
                1, 10, 1, List.of(new KtoFestivalSearchItemResponse(
                "keyword-15", "경복궁 야간관람", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                "https://images.example.test/main.jpg", null, "서울 종로구",
                "EV", "EV01", "EV010100", "축제"
        ))));

        mockMvc.perform(get("/admin/api/kto/festivals/search-by-keyword")
                        .param("keyword", " 경복궁 ")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].contentId").value("keyword-15"))
                .andExpect(jsonPath("$.items[0].categoryName").value("축제"));

        verify(ktoFestivalService).searchByKeyword("경복궁", 1, 10);
    }

    @Test
    void adminCanLoadFestivalAutofillDetail() throws Exception {
        when(ktoFestivalService.getDetail("12345")).thenReturn(new KtoFestivalAutofillResponse(
                "12345", "서울 축제", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                "https://images.example.test/main.jpg", "https://images.example.test/thumb.jpg",
                "서울 종로구", "광화문광장", "축제 소개", "10:00~21:00", "무료",
                "서울시", "02-120", "축제위원회", "02-0000-0000",
                "https://festival.example.test", "https://event.example.test", "02-1111-2222",
                "EV", "EV01", "EV010100", "축제"
        ));

        mockMvc.perform(get("/admin/api/kto/festivals/detail")
                        .param("contentId", " 12345 ")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentId").value("12345"))
                .andExpect(jsonPath("$.eventPlace").value("광화문광장"))
                .andExpect(jsonPath("$.eventHomepage").value("https://event.example.test"))
                .andExpect(jsonPath("$.categoryName").value("축제"));

        verify(ktoFestivalService).getDetail("12345");
    }

    @Test
    void invalidFestivalSearchAndDetailInputsReturnBadRequestWithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/admin/api/kto/festivals/search")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("행사 시작일을 yyyy-MM-dd 형식으로 입력해 주세요."));
        mockMvc.perform(get("/admin/api/kto/festivals/search")
                        .param("eventStartDate", "2026-09-01")
                        .param("eventEndDate", "2026-08-31")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("행사 종료일은 시작일보다 빠를 수 없습니다."));
        mockMvc.perform(get("/admin/api/kto/festivals/search")
                        .param("eventStartDate", "2026/09/01")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/api/kto/festivals/search")
                        .param("eventStartDate", "2026-09-01")
                        .param("pageNo", "0")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/api/kto/festivals/detail")
                        .param("contentId", "  ")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("축제 정보 식별값이 올바르지 않습니다."));
        mockMvc.perform(get("/admin/api/kto/festivals/search-by-keyword")
                        .param("keyword", "  ")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("축제·행사명을 입력해 주세요."));

        verify(ktoFestivalService, never()).search(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(ktoFestivalService, never()).getDetail(org.mockito.ArgumentMatchers.any());
        verify(ktoFestivalService, never()).searchByKeyword(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void festivalServiceFailuresUseExistingSafeStatusContract() throws Exception {
        when(ktoFestivalService.search(LocalDate.of(2026, 9, 1), null, 1, 10))
                .thenThrow(KtoTourApiException.missingApiKey());
        String configurationBody = mockMvc.perform(get("/admin/api/kto/festivals/search")
                        .param("eventStartDate", "2026-09-01")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("TourAPI 인증키가 설정되지 않았습니다."))
                .andReturn().getResponse().getContentAsString();

        when(ktoFestivalService.getDetail("12345")).thenThrow(KtoTourApiException.upstreamFailure());
        String upstreamBody = mockMvc.perform(get("/admin/api/kto/festivals/detail")
                        .param("contentId", "12345")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("관광정보를 불러오지 못했습니다."))
                .andReturn().getResponse().getContentAsString();

        assertThat(configurationBody).doesNotContain("org.springframework", "com.example.travlediary", "stackTrace");
        assertThat(upstreamBody).doesNotContain("org.springframework", "com.example.travlediary", "stackTrace");
    }

    @Test
    void keywordSearchUsesExistingSafeServiceFailureStatusContract() throws Exception {
        when(ktoFestivalService.searchByKeyword("경복궁", 1, 10))
                .thenThrow(KtoTourApiException.upstreamFailure());

        mockMvc.perform(get("/admin/api/kto/festivals/search-by-keyword")
                        .param("keyword", "경복궁")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("관광정보를 불러오지 못했습니다."));

        doThrow(KtoTourApiException.missingApiKey())
                .when(ktoFestivalService).searchByKeyword("경복궁", 1, 10);
        mockMvc.perform(get("/admin/api/kto/festivals/search-by-keyword")
                        .param("keyword", "경복궁")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("TourAPI 인증키가 설정되지 않았습니다."));
    }

    @Test
    void nonAdminsCannotUseFestivalEndpoints() throws Exception {
        mockMvc.perform(get("/admin/api/kto/festivals/search")
                        .param("eventStartDate", "2026-09-01")
                        .with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/api/kto/festivals/detail")
                        .param("contentId", "12345")
                        .with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/api/kto/festivals/search-by-keyword")
                        .param("keyword", "경복궁")
                        .with(user("member").roles("USER")))
                .andExpect(status().isForbidden());

        verify(ktoFestivalService, never()).search(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(ktoFestivalService, never()).getDetail(org.mockito.ArgumentMatchers.any());
        verify(ktoFestivalService, never()).searchByKeyword(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }
}
