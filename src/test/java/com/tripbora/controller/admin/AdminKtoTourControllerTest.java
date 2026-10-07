package com.tripbora.controller.admin;

import com.tripbora.config.CustomLoginSuccessHandler;
import com.tripbora.config.CustomLogoutSuccessHandler;
import com.tripbora.config.SecurityConfig;
import com.tripbora.dto.kto.KtoTourAutofillResponse;
import com.tripbora.dto.kto.KtoTourRegionMatchResponse;
import com.tripbora.dto.kto.KtoTourSearchItemResponse;
import com.tripbora.dto.kto.KtoTourSearchResponse;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.service.kto.KtoTourApiException;
import com.tripbora.service.destination.DestinationDuplicateCheck;
import com.tripbora.service.destination.DestinationDuplicateReason;
import com.tripbora.service.destination.DestinationDuplicateStatus;
import com.tripbora.service.kto.KtoTourDetailLookupService;
import com.tripbora.service.kto.KtoTourDuplicateMarker;
import com.tripbora.service.kto.KtoTourRegionMatchService;
import com.tripbora.service.kto.KtoTourService;
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

// 상세조회 + 지역 매칭 조합은 일괄등록과 공유하는 Service 라 실제 구현을 그대로 태운다.
@WebMvcTest(AdminKtoTourController.class)
@Import({SecurityConfig.class, KtoTourDetailLookupService.class})
class AdminKtoTourControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private KtoTourService ktoTourService;
    @MockitoBean
    private KtoTourRegionMatchService ktoTourRegionMatchService;
    @MockitoBean
    private KtoTourDuplicateMarker ktoTourDuplicateMarker;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void adminCanSearchAndLoadADetail() throws Exception {
        KtoTourSearchResponse searched = new KtoTourSearchResponse(
                1, 10, 1, List.of(new KtoTourSearchItemResponse(
                "126508", "12", "관광지", "창덕궁", "서울 종로구", "126.991", "37.579")));
        when(ktoTourService.search("창덕궁", 1, 10, null)).thenReturn(searched);
        // 검색 후보에는 일괄등록과 같은 공통 중복 판별 결과가 붙는다.
        when(ktoTourDuplicateMarker.markSearch(searched)).thenReturn(new KtoTourSearchResponse(1, 10, 1,
                List.of(searched.items().get(0).withDuplicate(new DestinationDuplicateCheck(
                        DestinationDuplicateStatus.REGISTERED, DestinationDuplicateReason.EXTERNAL_CONTENT_ID,
                        42L, "창덕궁", null, "같은 외부 콘텐츠 ID")))));
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
                .andExpect(jsonPath("$.items[0].contentTypeName").value("관광지"))
                .andExpect(jsonPath("$.items[0].duplicate.status").value("REGISTERED"))
                .andExpect(jsonPath("$.items[0].duplicate.destinationId").value(42))
                .andExpect(jsonPath("$.items[0].duplicate.message").value("같은 외부 콘텐츠 ID"));
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

        verify(ktoTourService).search("창덕궁", 1, 10, null);
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
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any());
        verify(ktoTourService, never()).getDetail(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void selectedDestinationTypeIsForwardedToTheSearchService() throws Exception {
        when(ktoTourService.search("국밥", 1, 10, "RESTAURANTS"))
                .thenReturn(new KtoTourSearchResponse(1, 10, 0, List.of()));

        mockMvc.perform(get("/admin/api/kto/tour/search")
                        .param("keyword", "국밥")
                        .param("destinationType", "RESTAURANTS")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        // contentTypeId 변환은 서버(KtoTourService)가 담당하고, 컨트롤러는 유형만 넘긴다.
        verify(ktoTourService).search("국밥", 1, 10, "RESTAURANTS");
    }

    @Test
    void configurationAndUpstreamFailuresUseSafeJsonStatuses() throws Exception {
        when(ktoTourService.search("창덕궁", 1, 10, null))
                .thenThrow(KtoTourApiException.missingApiKey());
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
                "org.springframework", "com.tripbora", "stackTrace", "serviceKey");
    }
}
