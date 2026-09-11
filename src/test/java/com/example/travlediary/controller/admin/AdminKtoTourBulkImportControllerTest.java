package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.kto.KtoTourAreaCandidateListResponse;
import com.example.travlediary.dto.kto.KtoTourAreaCandidateResponse;
import com.example.travlediary.dto.kto.KtoTourAreaResponse;
import com.example.travlediary.dto.kto.KtoTourBulkImportResponse;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.kto.KtoTourApiException;
import com.example.travlediary.service.kto.KtoTourBulkImportService;
import com.example.travlediary.service.kto.KtoTourCandidateRegistrationFilter;
import com.example.travlediary.service.kto.KtoTourService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminKtoTourBulkImportController.class)
@Import(SecurityConfig.class)
class AdminKtoTourBulkImportControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private KtoTourService ktoTourService;
    @MockitoBean
    private KtoTourBulkImportService ktoTourBulkImportService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void legalDongAreaListIsServedForTheDomesticRegionSelect() throws Exception {
        when(ktoTourService.getLegalDongAreas(null))
                .thenReturn(List.of(new KtoTourAreaResponse("11", "서울특별시")));
        when(ktoTourService.getLegalDongAreas("11"))
                .thenReturn(List.of(new KtoTourAreaResponse("110", "종로구")));

        mockMvc.perform(get("/admin/api/kto/tour/bulk/areas")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("11"))
                .andExpect(jsonPath("$[0].name").value("서울특별시"));
        mockMvc.perform(get("/admin/api/kto/tour/bulk/areas").param("regionCode", "11")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("110"))
                .andExpect(jsonPath("$[0].name").value("종로구"));
    }

    @Test
    void candidatesCarryTheRegistrationStateAndFilteredCountsAndAreNotSaved() throws Exception {
        when(ktoTourBulkImportService.findCandidates(
                "11", "110", "12", KtoTourCandidateRegistrationFilter.ALL, 1, 20)).thenReturn(
                new KtoTourAreaCandidateListResponse(1, 20, 2, 2, 1, 1, List.of(
                        new KtoTourAreaCandidateResponse("126508", "12", "관광지", "경복궁",
                                "서울 종로구", "https://tong.visitkorea.or.kr/a.jpg", true),
                        new KtoTourAreaCandidateResponse("126512", "12", "관광지", "광화문",
                                "서울 종로구", null, false))));

        mockMvc.perform(get("/admin/api/kto/tour/bulk/candidates")
                        .param("regionCode", "11").param("subRegionCode", "110")
                        .param("contentTypeId", "12")
                        .param("registrationFilter", "ALL")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].contentId").value("126508"))
                .andExpect(jsonPath("$.items[0].registered").value(true))
                .andExpect(jsonPath("$.items[1].registered").value(false))
                // 건수는 TourAPI 원본 총건수가 아니라 필터를 적용한 후보 기준이다.
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.allCount").value(2))
                .andExpect(jsonPath("$.newCount").value(1))
                .andExpect(jsonPath("$.registeredCount").value(1));

        verify(ktoTourBulkImportService, never()).importSelected(anyList(), any());
    }

    /** 필터를 지정하지 않으면 미등록만 본다. */
    @Test
    void theDefaultRegistrationFilterIsUnregistered() throws Exception {
        when(ktoTourBulkImportService.findCandidates(
                any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new KtoTourAreaCandidateListResponse(1, 20, 0, 0, 0, 0, List.of()));

        mockMvc.perform(get("/admin/api/kto/tour/bulk/candidates")
                        .param("regionCode", "11")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(ktoTourBulkImportService).findCandidates(
                "11", null, null, KtoTourCandidateRegistrationFilter.NEW, 1, 20);
    }

    @Test
    void candidateRequestsWithoutARegionAreRejected() throws Exception {
        mockMvc.perform(get("/admin/api/kto/tour/bulk/candidates")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지역을 선택해 주세요."));

        verify(ktoTourBulkImportService, never())
                .findCandidates(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void importReportsSuccessDuplicateAndFailureCounts() throws Exception {
        when(ktoTourBulkImportService.importSelected(anyList(), any()))
                .thenReturn(KtoTourBulkImportResponse.of(List.of(
                        KtoTourBulkImportResponse.ItemResult.success("126508", "창덕궁", 42L),
                        KtoTourBulkImportResponse.ItemResult.duplicate("126509", "경복궁"),
                        KtoTourBulkImportResponse.ItemResult.failed("126510", "종묘", "저장 실패"))));

        mockMvc.perform(post("/admin/api/kto/tour/bulk/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"contentId":"126508","contentTypeId":"12"},
                                          {"contentId":"126509","contentTypeId":"12"},
                                          {"contentId":"126510","contentTypeId":"12"}]}""")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.duplicateCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.results[2].contentId").value("126510"))
                .andExpect(jsonPath("$.results[2].title").value("종묘"))
                .andExpect(jsonPath("$.results[2].message").value("저장 실패"));
    }

    @Test
    void emptySelectionsAreRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(post("/admin/api/kto/tour/bulk/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}")
                        .with(user(adminPrincipal())))
                .andExpect(status().isBadRequest());

        verify(ktoTourBulkImportService, never()).importSelected(anyList(), any());
    }

    @Test
    void upstreamFailuresUseSafeJsonStatuses() throws Exception {
        when(ktoTourService.getLegalDongAreas(null)).thenThrow(KtoTourApiException.missingApiKey());

        mockMvc.perform(get("/admin/api/kto/tour/bulk/areas")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("TourAPI 인증키가 설정되지 않았습니다."));
    }

    private CustomUserDetails adminPrincipal() {
        User admin = new User();
        admin.setId(7L);
        admin.setUsername("admin");
        admin.setUserPassword("{noop}password");
        admin.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(admin);
    }
}
