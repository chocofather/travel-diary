package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.wikidata.WikidataDestinationCandidate;
import com.example.travlediary.dto.wikidata.WikidataDestinationPreview;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.wikidata.WikidataApiException;
import com.example.travlediary.service.wikidata.WikidataDestinationService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminWikidataDestinationController.class)
@Import(SecurityConfig.class)
class AdminWikidataDestinationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WikidataDestinationService destinationService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void adminCanSearchAndPreviewWithoutSaving() throws Exception {
        when(destinationService.search("에펠탑")).thenReturn(List.of(
                new WikidataDestinationCandidate("Q243", "에펠탑", "ko", "파리의 탑", "ko",
                        "프랑스", "파리", null, null)));
        when(destinationService.preview("Q243")).thenReturn(new WikidataDestinationPreview(
                "Q243", Map.of("ko", "에펠탑", "en", "Eiffel Tower"), Map.of("en", "tower"),
                "Q142", "프랑스", List.of("파리"), 48.858296, 2.294479,
                null, null, null,
                new WikidataDestinationPreview.RegionMatch(17L, 106L, true, "확인 필요")));

        mockMvc.perform(get("/admin/api/wikidata/destinations/search")
                        .param("keyword", "에펠탑").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].qid").value("Q243"))
                .andExpect(jsonPath("$[0].country").value("프랑스"));
        mockMvc.perform(get("/admin/api/wikidata/destinations/preview")
                        .param("qid", "Q243").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names.ko").value("에펠탑"))
                .andExpect(jsonPath("$.names.zh-TW").doesNotExist())
                .andExpect(jsonPath("$.regionMatch.matched").value(true));
    }

    @Test
    void invalidInputAndUpstreamErrorHaveDifferentStatuses() throws Exception {
        when(destinationService.search("x")).thenThrow(new IllegalArgumentException("검색어를 2~100자로 입력해 주세요."));
        when(destinationService.preview("Q243")).thenThrow(new WikidataApiException("Wikidata에 연결하지 못했습니다."));

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
        verifyNoInteractions(destinationService);
    }
}
