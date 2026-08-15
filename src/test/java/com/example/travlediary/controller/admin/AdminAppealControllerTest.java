package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.AdminAppealDto;
import com.example.travlediary.model.AppealStatus;
import com.example.travlediary.model.SanctionStatus;
import com.example.travlediary.model.SanctionType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AdminAppealService;
import com.example.travlediary.service.user.AppealValidationException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminAppealController.class)
@Import(SecurityConfig.class)
class AdminAppealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAppealService adminAppealService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void listShowsAppealsWithMemberAndSanctionSummary() throws Exception {
        when(adminAppealService.countAppeals(null, null)).thenReturn(1L);
        when(adminAppealService.getAppeals(null, null, 0L, 20)).thenReturn(List.of(appeal()));

        Document document = adminPage("/admin/appeals");

        assertThat(document.select(".admin-appeals-table thead th").eachText())
                .containsExactly("상태", "회원", "제재", "이의제기 내용", "제출일", "처리");
        assertThat(document.selectFirst(".admin-appeal-status").text()).isEqualTo("접수됨");
        assertThat(document.selectFirst(".admin-appeal-member strong").text()).isEqualTo("travler");
        assertThat(document.selectFirst(".admin-appeal-sanction").text()).isEqualTo("기간제한");
        assertThat(document.select(".admin-appeals-table tbody a").eachAttr("href"))
                .contains("/admin/appeals/30");
    }

    @Test
    void statusFilterAndMemberSearchAreForwarded() throws Exception {
        when(adminAppealService.countAppeals(AppealStatus.PENDING, "travler")).thenReturn(0L);
        when(adminAppealService.getAppeals(AppealStatus.PENDING, "travler", 0L, 20))
                .thenReturn(List.of());

        mockMvc.perform(get("/admin/appeals")
                        .param("status", "PENDING").param("keyword", "  travler  ")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/appeals/list"))
                .andExpect(model().attribute("currentStatus", "PENDING"))
                .andExpect(model().attribute("keyword", "travler"));

        verify(adminAppealService).getAppeals(AppealStatus.PENDING, "travler", 0L, 20);
    }

    @Test
    void unknownOrDraftStatusFallsBackToAll() throws Exception {
        when(adminAppealService.countAppeals(null, null)).thenReturn(0L);
        when(adminAppealService.getAppeals(null, null, 0L, 20)).thenReturn(List.of());

        for (String status : new String[]{"UNKNOWN", "DRAFT"}) {
            mockMvc.perform(get("/admin/appeals").param("status", status).with(user(admin())))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("currentStatus", "ALL"));
        }
    }

    @Test
    void detailShowsMemberSanctionAndHandlingForm() throws Exception {
        when(adminAppealService.getAppeal(30L)).thenReturn(appeal());

        Document document = adminPage("/admin/appeals/30");

        assertThat(document.select(".admin-appeal-meta dd").eachText())
                .contains("travler", "여행자", "user@example.com", "적용중", "이용약관 위반");
        assertThat(document.selectFirst(".admin-appeal-body").text()).isEqualTo("소명합니다");

        var form = document.selectFirst("#appeal-handle-form");
        assertThat(form.attr("action")).isEqualTo("/admin/appeals/30/approve");
        assertThat(form.selectFirst("textarea[name=adminReply]").hasAttr("required")).isTrue();
        assertThat(form.select("button[formaction='/admin/appeals/30/reject']")).hasSize(1);
    }

    @Test
    void pendingAppealOnAnActiveSanctionShowsBothActionsWithoutWarning() throws Exception {
        when(adminAppealService.getAppeal(30L)).thenReturn(appeal());

        Document document = adminPage("/admin/appeals/30");

        assertThat(document.select(".admin-appeal-hint")).isEmpty();
        assertThat(document.selectFirst("#appeal-handle-form").attr("action"))
                .isEqualTo("/admin/appeals/30/approve");
        assertThat(document.select("#appeal-handle-form button").eachText())
                .containsExactly("승인 (제한 해제)", "기각");
    }

    @Test
    void pendingAppealOnAFinishedSanctionWarnsAndHidesApproval() throws Exception {
        AdminAppealDto stale = appeal();
        stale.setSanctionStatus(SanctionStatus.EXPIRED);
        when(adminAppealService.getAppeal(30L)).thenReturn(stale);

        Document document = adminPage("/admin/appeals/30");

        assertThat(document.selectFirst(".admin-appeal-hint.is-warning").text())
                .isEqualTo("이미 해제되었거나 만료된 제재라 승인으로 추가 해제할 수 없습니다.");
        // 승인 버튼이 사라지고 기본 전송도 기각으로 향한다
        assertThat(document.select("#appeal-handle-form button").eachText())
                .containsExactly("기각");
        assertThat(document.selectFirst("#appeal-handle-form").attr("action"))
                .isEqualTo("/admin/appeals/30/reject");
    }

    @Test
    void approvedAppealShowsSuccessNoticeInsteadOfTheStaleWarning() throws Exception {
        AdminAppealDto approved = appeal();
        approved.setStatus(AppealStatus.APPROVED);
        approved.setSanctionStatus(SanctionStatus.LIFTED);
        approved.setAdminReply("소명 인정");
        when(adminAppealService.getAppeal(30L)).thenReturn(approved);

        Document document = adminPage("/admin/appeals/30");

        assertThat(document.selectFirst(".admin-appeal-hint.is-success").text())
                .isEqualTo("이의제기가 승인되어 이용제한이 해제되었습니다.");
        assertThat(document.select(".admin-appeal-hint.is-warning")).isEmpty();
        assertThat(document.select("#appeal-handle-form")).isEmpty();
    }

    @Test
    void rejectedAppealShowsTheRejectionNoticeAndAdminReply() throws Exception {
        AdminAppealDto rejected = appeal();
        rejected.setStatus(AppealStatus.REJECTED);
        rejected.setAdminReply("사유 불충분");
        rejected.setHandledAdminName("master");
        rejected.setHandledAt(LocalDateTime.of(2026, 8, 16, 9, 0));
        when(adminAppealService.getAppeal(30L)).thenReturn(rejected);

        Document document = adminPage("/admin/appeals/30");

        assertThat(document.selectFirst(".admin-appeal-hint.is-rejected").text())
                .isEqualTo("이의제기가 기각되어 이용제한이 그대로 유지됩니다.");
        assertThat(document.select(".admin-appeal-hint.is-warning")).isEmpty();
        assertThat(document.select(".admin-appeal-meta dd").eachText())
                .contains("기각됨", "사유 불충분", "master");
    }

    @Test
    void handledAppealShowsTheResultInsteadOfTheForm() throws Exception {
        AdminAppealDto handled = appeal();
        handled.setStatus(AppealStatus.APPROVED);
        handled.setAdminReply("소명 인정");
        handled.setHandledAdminName("master");
        handled.setHandledAt(LocalDateTime.of(2026, 8, 16, 9, 0));
        when(adminAppealService.getAppeal(30L)).thenReturn(handled);

        Document document = adminPage("/admin/appeals/30");

        assertThat(document.select("#appeal-handle-form")).isEmpty();
        assertThat(document.select(".admin-appeal-meta dd").eachText())
                .contains("승인됨", "master", "2026-08-16 09:00", "소명 인정");
    }

    @Test
    void approveAndRejectCallTheServiceWithTheAdminReply() throws Exception {
        mockMvc.perform(post("/admin/appeals/30/approve")
                        .with(user(admin())).with(csrf())
                        .param("adminReply", "소명 인정"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/appeals/30"));
        verify(adminAppealService).approve(30L, "소명 인정", 1L);

        mockMvc.perform(post("/admin/appeals/30/reject")
                        .with(user(admin())).with(csrf())
                        .param("adminReply", "사유 불충분"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/appeals/30"));
        verify(adminAppealService).reject(30L, "사유 불충분", 1L);
    }

    @Test
    void rejectedHandlingRendersTheDetailWithTheMessage() throws Exception {
        when(adminAppealService.getAppeal(30L)).thenReturn(appeal());
        doThrow(new AppealValidationException(null, "이미 처리된 이의제기입니다."))
                .when(adminAppealService).approve(eq(30L), any(), eq(1L));

        var result = mockMvc.perform(post("/admin/appeals/30/approve")
                        .with(user(admin())).with(csrf())
                        .param("adminReply", "소명 인정"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/appeals/detail"))
                .andReturn();

        assertThat(Jsoup.parse(result.getResponse().getContentAsString())
                .selectFirst(".admin-alert").text())
                .isEqualTo("이미 처리된 이의제기입니다.");
    }

    @Test
    void missingAppealReturnsNotFound() throws Exception {
        when(adminAppealService.getAppeal(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/admin/appeals/99").with(user(admin())))
                .andExpect(status().isNotFound());
    }

    @Test
    void appealEndpointsRequireCsrfAndAdminRole() throws Exception {
        mockMvc.perform(post("/admin/appeals/30/approve")
                        .with(user(admin())).param("adminReply", "사유"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/appeals/30/reject")
                        .with(user("member").roles("USER")).with(csrf())
                        .param("adminReply", "사유"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/appeals").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());

        verify(adminAppealService, never()).approve(anyLong(), any(), anyLong());
        verify(adminAppealService, never()).reject(anyLong(), any(), anyLong());
    }

    private Document adminPage(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(user(admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private AdminAppealDto appeal() {
        AdminAppealDto appeal = new AdminAppealDto();
        appeal.setId(30L);
        appeal.setStatus(AppealStatus.PENDING);
        appeal.setContent("소명합니다");
        appeal.setSubmittedAt(LocalDateTime.of(2026, 8, 15, 12, 0));
        appeal.setUserId(5L);
        appeal.setUsername("travler");
        appeal.setNickname("여행자");
        appeal.setUserEmail("user@example.com");
        appeal.setSanctionId(10L);
        appeal.setSanctionType(SanctionType.TEMPORARY);
        appeal.setSanctionStatus(SanctionStatus.ACTIVE);
        appeal.setSanctionReason("이용약관 위반");
        appeal.setSanctionStartsAt(LocalDateTime.of(2026, 8, 15, 9, 0));
        appeal.setSanctionExpiresAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        return appeal;
    }

    private CustomUserDetails admin() {
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("master");
        adminUser.setUserPassword("password");
        adminUser.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(adminUser);
    }
}
