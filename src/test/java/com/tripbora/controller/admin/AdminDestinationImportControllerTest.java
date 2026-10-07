package com.tripbora.controller.admin;

import com.tripbora.config.CustomLoginSuccessHandler;
import com.tripbora.config.CustomLogoutSuccessHandler;
import com.tripbora.config.SecurityConfig;
import com.tripbora.dto.destinationimport.DestinationImportPreview;
import com.tripbora.dto.destinationimport.DestinationImportResult;
import com.tripbora.model.User;
import com.tripbora.model.UserRole;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.security.CustomUserDetails;
import com.tripbora.service.destinationimport.DestinationImportMasterExportService;
import com.tripbora.service.destinationimport.DestinationImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** JSON 일괄등록 API: 관리자만, 상태를 바꾸는 요청은 CSRF 토큰이 필요하고, 본문은 그대로 서비스에 넘긴다. */
@WebMvcTest(AdminDestinationImportController.class)
@Import(SecurityConfig.class)
class AdminDestinationImportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DestinationImportService importService;
    @MockitoBean private DestinationImportMasterExportService masterExportService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void previewPassesTheRawJsonAndRegisterUsesTheLoggedInAdmin() throws Exception {
        String json = "{\"version\": 1, \"destinations\": []}";
        when(importService.preview(json)).thenReturn(DestinationImportPreview.of(List.of()));
        String body = "{\"index\": 0, \"allowPossibleDuplicate\": false, \"item\": {}}";
        when(importService.register(body, 7L)).thenReturn(DestinationImportResult.success(0, "a", 42L));

        mockMvc.perform(post("/admin/api/destinations/import-json/preview")
                        .contentType(MediaType.APPLICATION_JSON).content(json)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.summary.registrable").value(0));
        mockMvc.perform(post("/admin/api/destinations/import-json/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(user(new CustomUserDetails(admin()))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.destinationId").value(42));

        verify(importService).preview(json);
        verify(importService).register(body, 7L);
    }

    @Test
    void masterDataIsDownloadedAsAJsonFile() throws Exception {
        when(masterExportService.export()).thenReturn(new DestinationImportMasterExportService.MasterExport(
                "tripbora-destination-import-master", 1, "2026-10-08T00:00:00+09:00", List.of(), null,
                Map.of("domestic", List.of()), Map.of("ATTRACTION", List.of("궁궐·역사")), Map.of()));

        mockMvc.perform(get("/admin/api/destinations/import-json/master").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"destination-import-master.json\""))
                .andExpect(jsonPath("$.categoriesByType.ATTRACTION[0]").value("궁궐·역사"));
    }

    @Test
    void onlyAdminsWithACsrfTokenCanUseIt() throws Exception {
        mockMvc.perform(post("/admin/api/destinations/import-json/preview")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(user("user").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/api/destinations/import-json/master").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/api/destinations/import-json/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(user(new CustomUserDetails(admin()))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(importService, masterExportService);
    }

    private static User admin() {
        User admin = new User();
        admin.setId(7L);
        admin.setUserPassword("password");
        admin.setUserRole(UserRole.ADMIN);
        return admin;
    }
}
