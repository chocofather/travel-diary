package com.tripbora.controller.admin;

import com.tripbora.config.CustomLoginSuccessHandler;
import com.tripbora.config.CustomLogoutSuccessHandler;
import com.tripbora.config.SecurityConfig;
import com.tripbora.model.User;
import com.tripbora.model.UserRole;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.security.CustomUserDetails;
import com.tripbora.service.wikidata.WikidataBulkRegistrationService;
import com.tripbora.service.wikidata.WikidataRegionExplorer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminWikidataBulkImportController.class)
@Import(SecurityConfig.class)
class AdminWikidataBulkImportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WikidataBulkRegistrationService bulkRegistrationService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void searchIsAdminOnlyAndReturnsPlacesWithTheNextPage() throws Exception {
        when(bulkRegistrationService.search("에펠탑", 10)).thenReturn(new WikidataBulkRegistrationService.SearchResult(
                List.of(new WikidataBulkRegistrationService.Candidate("Q243", "에펠탑", "파리의 탑", "프랑스", "파리",
                        null, true)), 2, 20));

        mockMvc.perform(get("/admin/api/wikidata/bulk/search").param("keyword", "에펠탑").param("offset", "10")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].qid").value("Q243"))
                .andExpect(jsonPath("$.candidates[0].registered").value(true))
                .andExpect(jsonPath("$.excludedCount").value(2))
                .andExpect(jsonPath("$.nextOffset").value(20));
        mockMvc.perform(get("/admin/api/wikidata/bulk/search").param("keyword", "에펠탑")
                        .with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void regionExplorationIsAdminOnlyAndPassesTheChosenCityAndPage() throws Exception {
        var resolution = new WikidataRegionExplorer.Resolution(55L, "후쿠오카", "Q123258", "후쿠오카현",
                "ISO 3166-2 지역 코드 JP-40 일치", null, List.of(new WikidataRegionExplorer.Area("Q26600", "후쿠오카시")));
        when(bulkRegistrationService.resolveRegion(55L)).thenReturn(resolution);
        when(bulkRegistrationService.regionSearch(55L, "Q26600", 20)).thenReturn(
                new WikidataBulkRegistrationService.RegionSearchResult(resolution, "Q26600", List.of(), 0, 40, 191, false));

        mockMvc.perform(get("/admin/api/wikidata/bulk/regions/resolve").param("regionId", "55")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qid").value("Q123258"))
                .andExpect(jsonPath("$.municipalities[0].name").value("후쿠오카시"));
        mockMvc.perform(get("/admin/api/wikidata/bulk/regions/candidates").param("regionId", "55")
                        .param("cityQid", "Q26600").param("offset", "20").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaQid").value("Q26600"))
                .andExpect(jsonPath("$.nextOffset").value(40))
                .andExpect(jsonPath("$.total").value(191));
        mockMvc.perform(get("/admin/api/wikidata/bulk/regions/resolve").param("regionId", "55")
                        .with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationNeedsCsrfAndSavesOneDestinationPerRequest() throws Exception {
        String body = """
                {"qid":"Q243","type":"ATTRACTION","season":"SPRING","regionId":null,"koreanName":null,
                 "photoFileName":"Tour Eiffel.jpg"}""";
        var request = new WikidataBulkRegistrationService.RegisterRequest(
                "Q243", "ATTRACTION", "SPRING", null, null, "Tour Eiffel.jpg");
        when(bulkRegistrationService.register(eq(request), eq(7L)))
                .thenReturn(new WikidataBulkRegistrationService.ItemResult("Q243", "SUCCESS", 501L, null));

        mockMvc.perform(post("/admin/api/wikidata/bulk/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body).with(user(admin())))
                .andExpect(status().isForbidden());
        verify(bulkRegistrationService, never()).register(any(), any());

        mockMvc.perform(post("/admin/api/wikidata/bulk/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body).with(user(admin())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.destinationId").value(501));
    }

    private CustomUserDetails admin() {
        User user = new User();
        user.setId(7L);
        user.setUserPassword("password");
        user.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(user);
    }
}
