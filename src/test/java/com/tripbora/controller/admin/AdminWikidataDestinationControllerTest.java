package com.tripbora.controller.admin;

import com.tripbora.config.CustomLoginSuccessHandler;
import com.tripbora.config.CustomLogoutSuccessHandler;
import com.tripbora.config.SecurityConfig;
import com.tripbora.dto.wikidata.WikidataDestinationCandidate;
import com.tripbora.dto.wikidata.WikidataDestinationPreview;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.service.wikidata.WikidataApiException;
import com.tripbora.service.wikidata.WikidataDestinationService;
import com.tripbora.service.wikidata.WikipediaDescriptionService;
import com.tripbora.dto.wikidata.WikipediaDescriptionPreview;
import com.tripbora.dto.wikidata.CommonsPhotoPreview;
import com.tripbora.service.wikidata.CommonsPhotoPreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminWikidataDestinationController.class)
@Import(SecurityConfig.class)
class AdminWikidataDestinationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WikidataDestinationService destinationService;
    @MockitoBean private WikipediaDescriptionService wikipediaDescriptionService;
    @MockitoBean private CommonsPhotoPreviewService commonsPhotoPreviewService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void adminCanSearchAndPreviewWithoutSaving() throws Exception {
        when(destinationService.quickSearch("에펠탑")).thenReturn(List.of(
                new WikidataDestinationCandidate("Q243", "에펠탑", "ko", "파리의 탑", "ko",
                        null, null, null, null)));
        when(destinationService.searchDetails(List.of("Q243", "Q90"))).thenReturn(List.of(
                new WikidataDestinationCandidate("Q243", "에펠탑", "ko", "파리의 탑", "ko",
                        "프랑스", "파리", null, null)));
        when(destinationService.previewForAutofill("Q243")).thenReturn(new WikidataDestinationPreview(
                "Q243", Map.of("ko", "에펠탑", "en", "Eiffel Tower"), Map.of("en", "tower"),
                "Q142", "프랑스", List.of("파리"), 48.858296, 2.294479,
                null, null, null,
                new WikidataDestinationPreview.RegionMatch(17L, 106L, true, "확인 필요", List.of(
                        new WikidataDestinationPreview.RegionMatch.RegionPathItem(16L, "유럽"),
                        new WikidataDestinationPreview.RegionMatch.RegionPathItem(17L, "프랑스"),
                        new WikidataDestinationPreview.RegionMatch.RegionPathItem(106L, "파리"))),
                new WikidataDestinationPreview.TravelInfo("https://www.toureiffel.paris/en", null, false, false),
                Map.of()));

        mockMvc.perform(get("/admin/api/wikidata/destinations/search")
                        .param("keyword", "에펠탑").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().exists("Server-Timing"))
                .andExpect(jsonPath("$[0].qid").value("Q243"))
                .andExpect(jsonPath("$[0].country").doesNotExist());
        mockMvc.perform(get("/admin/api/wikidata/destinations/search-details")
                        .param("qids", "Q243", "Q90").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].country").value("프랑스"));
        mockMvc.perform(get("/admin/api/wikidata/destinations/preview")
                        .param("qid", "Q243").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names.ko").value("에펠탑"))
                .andExpect(jsonPath("$.names.zh-TW").doesNotExist())
                .andExpect(jsonPath("$.regionMatch.matched").value(true))
                .andExpect(jsonPath("$.regionMatch.path[2].id").value(106));
    }

    @Test
    void invalidInputAndUpstreamErrorHaveDifferentStatuses() throws Exception {
        when(destinationService.quickSearch("x")).thenThrow(new IllegalArgumentException("검색어를 2~100자로 입력해 주세요."));
        when(destinationService.previewForAutofill("Q243")).thenThrow(new WikidataApiException("Wikidata에 연결하지 못했습니다."));
        when(destinationService.searchDetails(List.of("bad"))).thenThrow(
                new IllegalArgumentException("올바른 Wikidata QID 목록이 아닙니다."));

        mockMvc.perform(get("/admin/api/wikidata/destinations/search-details")
                        .param("qids", "bad").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/admin/api/wikidata/destinations/search")
                        .param("keyword", "x").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("검색어를 2~100자로 입력해 주세요."));
        mockMvc.perform(get("/admin/api/wikidata/destinations/preview")
                        .param("qid", "Q243").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Wikidata에 연결하지 못했습니다."));
    }

    @Test
    void userCannotAccessAdminSearch() throws Exception {
        mockMvc.perform(get("/admin/api/wikidata/destinations/search")
                        .param("keyword", "Eiffel Tower").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/api/wikidata/destinations/search-details")
                        .param("qids", "Q243").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(destinationService);
    }

    @Test
    void wikipediaPreviewIsReadOnlyAndAdminOnly() throws Exception {
        when(wikipediaDescriptionService.previewForAutofill("Q243"))
                .thenReturn(new WikipediaDescriptionPreview("Q243", List.of()));
        mockMvc.perform(get("/admin/api/wikidata/destinations/wikipedia")
                        .param("qid", "Q243").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qid").value("Q243"));
        mockMvc.perform(get("/admin/api/wikidata/destinations/wikipedia")
                        .param("qid", "Q243").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void commonsPhotoPreviewIsAdminOnlyAndDoesNotSave() throws Exception {
        when(commonsPhotoPreviewService.preview("Q243", null))
                .thenReturn(new CommonsPhotoPreview("Q243", "Eiffel Tower", "AVAILABLE", null, List.of(),
                        "16:file|ab|1", 5));
        when(commonsPhotoPreviewService.preview("Q243", "16:file|ab|1"))
                .thenReturn(new CommonsPhotoPreview("Q243", "Eiffel Tower", "AVAILABLE", null, List.of(), null, 5));
        mockMvc.perform(get("/admin/api/wikidata/destinations/commons-photos")
                        .param("qid", "Q243").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qid").value("Q243"))
                .andExpect(jsonPath("$.nextCursor").value("16:file|ab|1"))
                .andExpect(jsonPath("$.selectionLimit").value(5));
        // '사진 더 보기'는 앞 묶음이 준 위치를 그대로 넘긴다.
        mockMvc.perform(get("/admin/api/wikidata/destinations/commons-photos")
                        .param("qid", "Q243").param("cursor", "16:file|ab|1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mockMvc.perform(get("/admin/api/wikidata/destinations/commons-photos")
                        .param("qid", "Q243").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
