package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.DiaryCoverLibraryReportDetailDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportListItemDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportPageDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportStatusFilter;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryItemStatus;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAssetStatus;
import com.example.travlediary.model.DiaryCoverLibraryReport;
import com.example.travlediary.model.DiaryCoverLibraryReportReason;
import com.example.travlediary.model.DiaryCoverLibraryReportStatus;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.diary.DiaryStickerMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryCoverLibraryModerationService;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({AdminDiaryCoverLibraryReportController.class,
        AdminDiaryCoverLibraryRestoreController.class})
@Import({SecurityConfig.class, DiaryStickerCatalog.class})
class AdminDiaryCoverLibraryReportControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DiaryCoverLibraryModerationService moderationService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;
    @MockitoBean private DiaryStickerMapper diaryStickerMapper;

    @Test
    void defaultListShowsPendingReports() throws Exception {
        DiaryCoverLibraryReportListItemDto row = row();
        when(moderationService.getReports(1L,
                DiaryCoverLibraryReportStatusFilter.PENDING, 1))
                .thenReturn(new DiaryCoverLibraryReportPageDto(
                        List.of(row), DiaryCoverLibraryReportStatusFilter.PENDING,
                        1, 1, 1, 20));

        Document document = Jsoup.parse(mockMvc.perform(
                        get("/admin/cover-library/reports").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/cover-library-reports/list"))
                .andReturn().getResponse().getContentAsString());

        assertThat(document.select(".admin-cover-report-table tbody tr")).hasSize(1);
        assertThat(document.text()).contains("#50", "표지", "여름 표지", "공유 당시 이름",
                "저작권 침해", "신고자", "대기 중");
        verify(moderationService).getReports(1L,
                DiaryCoverLibraryReportStatusFilter.PENDING, 1);
    }

    @Test
    void allStatusFilterIsPassedToTheService() throws Exception {
        when(moderationService.getReports(1L,
                DiaryCoverLibraryReportStatusFilter.ALL, 2))
                .thenReturn(new DiaryCoverLibraryReportPageDto(
                        List.of(), DiaryCoverLibraryReportStatusFilter.ALL,
                        2, 2, 21, 20));

        mockMvc.perform(get("/admin/cover-library/reports")
                        .param("status", "ALL").param("page", "2")
                        .with(user(admin())))
                .andExpect(status().isOk());

        verify(moderationService).getReports(1L,
                DiaryCoverLibraryReportStatusFilter.ALL, 2);
    }

    @Test
    void detailShowsSnapshotAndPendingActions() throws Exception {
        DiaryCoverLibraryReport report = new DiaryCoverLibraryReport();
        report.setId(50L);
        report.setLibraryItemId(11L);
        report.setReportedSnapshotVersion(3);
        report.setReasonCode(DiaryCoverLibraryReportReason.COPYRIGHT);
        report.setDescription("권리 침해 의심");
        report.setStatus(DiaryCoverLibraryReportStatus.PENDING);
        DiaryCoverLibraryItem item = item();
        when(moderationService.getReportDetail(1L, 50L))
                .thenReturn(new DiaryCoverLibraryReportDetailDto(
                        report, item, List.of(), null, "신고자"));

        Document document = Jsoup.parse(mockMvc.perform(
                        get("/admin/cover-library/reports/50").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/cover-library-reports/detail"))
                .andReturn().getResponse().getContentAsString());

        assertThat(document.text()).contains("신고 #50", "snapshot 3", "여름 표지",
                "공유 당시 이름", "신고자", "저작권 침해", "권리 침해 의심");
        assertThat(document.select("form[action='/admin/cover-library/reports/50/process']"))
                .hasSize(1);
        assertThat(document.select("option[value=BLOCK_PHOTO]")).isEmpty();
    }

    @Test
    void processDelegatesToTheTransactionalService() throws Exception {
        mockMvc.perform(post("/admin/cover-library/reports/50/process")
                        .with(user(admin())).with(csrf())
                        .param("decision", "BLOCK_ITEM")
                        .param("reason", "권리 침해 확인")
                        .param("adminNote", "검토 완료"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/cover-library/reports/50"))
                .andExpect(flash().attribute("coverReportMessage", "신고 처리를 완료했습니다."));

        verify(moderationService).process(eq(1L), eq(50L), any());
    }

    @Test
    void blockedItemAndPhotoShowIndependentRestoreForms() throws Exception {
        DiaryCoverLibraryReport report = new DiaryCoverLibraryReport();
        report.setId(50L);
        report.setLibraryItemId(11L);
        report.setPhotoAssetId(701L);
        report.setReportedSnapshotVersion(3);
        report.setReasonCode(DiaryCoverLibraryReportReason.COPYRIGHT);
        report.setStatus(DiaryCoverLibraryReportStatus.RESOLVED);
        DiaryCoverLibraryItem item = item();
        item.setStatus(DiaryCoverLibraryItemStatus.BLOCKED);
        DiaryCoverLibraryPhotoAsset asset = new DiaryCoverLibraryPhotoAsset();
        asset.setId(701L);
        asset.setLibraryItemId(11L);
        asset.setStatus(DiaryCoverLibraryPhotoAssetStatus.BLOCKED);
        when(moderationService.getReportDetail(1L, 50L))
                .thenReturn(new DiaryCoverLibraryReportDetailDto(
                        report, item, List.of(), asset, "신고자"));

        Document document = Jsoup.parse(mockMvc.perform(
                        get("/admin/cover-library/reports/50").with(user(admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(document.select(
                "form[action='/admin/cover-library/items/11/restore']")).hasSize(1);
        assertThat(document.select(
                "form[action='/admin/cover-library/photo-assets/701/restore']")).hasSize(1);
        assertThat(document.text()).contains("표지 차단 해제", "사진 차단 해제", "복구 사유");
    }

    @Test
    void restoreEndpointsUseTheBlockingActionsReportAndIgnoreClientReportIds()
            throws Exception {
        when(moderationService.restoreItem(eq(1L), eq(11L), any())).thenReturn(50L);
        when(moderationService.restorePhoto(eq(1L), eq(701L), any())).thenReturn(51L);

        mockMvc.perform(post("/admin/cover-library/items/11/restore")
                        .with(user(admin())).with(csrf())
                        .param("reportId", "999")
                        .param("reason", "오차단 확인"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/cover-library/reports/50"))
                .andExpect(flash().attribute("coverReportMessage", "표지 차단을 해제했습니다."));

        mockMvc.perform(post("/admin/cover-library/photo-assets/701/restore")
                        .with(user(admin())).with(csrf())
                        .param("reportId", "999")
                        .param("reason", "권리 관계 해결"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/cover-library/reports/51"))
                .andExpect(flash().attribute("coverReportMessage", "사진 차단을 해제했습니다."));

        verify(moderationService).restoreItem(eq(1L), eq(11L), any());
        verify(moderationService).restorePhoto(eq(1L), eq(701L), any());
    }

    @Test
    void restoreEndpointsRequireAdminAndCsrf() throws Exception {
        mockMvc.perform(post("/admin/cover-library/items/11/restore")
                        .with(user("member").roles("USER")).with(csrf())
                        .param("reason", "복구"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/cover-library/photo-assets/701/restore")
                        .with(user(admin())).param("reason", "복구"))
                .andExpect(status().isForbidden());

        verify(moderationService, never()).restoreItem(any(), any(), any());
        verify(moderationService, never()).restorePhoto(any(), any(), any());
    }

    @Test
    void malformedPhotoAssetIdReturnsASafeFlashMessage() throws Exception {
        mockMvc.perform(post("/admin/cover-library/reports/50/process")
                        .with(user(admin())).with(csrf())
                        .param("decision", "BLOCK_PHOTO")
                        .param("photoAssetId", "not-a-number")
                        .param("reason", "권리 침해 확인"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/cover-library/reports/50"))
                .andExpect(flash().attribute("coverReportError", "잘못된 처리 요청입니다."));

        verify(moderationService, never()).process(any(), any(), any());
    }

    @Test
    void reportAdministrationRequiresAdminAndCsrf() throws Exception {
        mockMvc.perform(get("/admin/cover-library/reports")
                        .with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/cover-library/reports/50/process")
                        .with(user(admin())).param("decision", "REJECT"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/cover-library/reports/50/process")
                        .with(user("member").roles("USER")).with(csrf())
                        .param("decision", "REJECT"))
                .andExpect(status().isForbidden());

        verify(moderationService, never()).process(any(), any(), any());
    }

    private DiaryCoverLibraryReportListItemDto row() {
        DiaryCoverLibraryReportListItemDto row = new DiaryCoverLibraryReportListItemDto();
        row.setReportId(50L);
        row.setLibraryItemId(11L);
        row.setLibraryTitle("여름 표지");
        row.setCreatorDisplayName("공유 당시 이름");
        row.setReasonCode(DiaryCoverLibraryReportReason.COPYRIGHT);
        row.setReporterDisplayName("신고자");
        row.setStatus(DiaryCoverLibraryReportStatus.PENDING);
        row.setCreatedAt(Timestamp.from(Instant.parse("2026-09-17T03:00:00Z")));
        return row;
    }

    private DiaryCoverLibraryItem item() {
        DiaryCoverLibraryItem item = new DiaryCoverLibraryItem();
        item.setId(11L);
        item.setTitle("여름 표지");
        item.setCreatorDisplayName("공유 당시 이름");
        item.setStatus(DiaryCoverLibraryItemStatus.PUBLISHED);
        item.setSnapshotVersion(4);
        item.setBaseCoverStyle("DEFAULT");
        return item;
    }

    private CustomUserDetails admin() {
        User user = new User();
        user.setId(1L);
        user.setUsername("master");
        user.setUserPassword("password");
        user.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(user);
    }
}
