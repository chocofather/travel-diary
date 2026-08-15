package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.model.AppealStatus;
import com.example.travlediary.model.SanctionStatus;
import com.example.travlediary.model.SanctionType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserAppeal;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserSanction;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AppealValidationException;
import com.example.travlediary.service.user.UserAppealService;
import com.example.travlediary.service.user.UserSanctionService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AccountRestrictedController.class)
@Import(SecurityConfig.class)
class AccountRestrictedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSanctionService userSanctionService;
    @MockitoBean
    private UserAppealService userAppealService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void temporaryRestrictionShowsTypeReasonAndBothDates() throws Exception {
        when(userSanctionService.getActiveSanction(5L))
                .thenReturn(sanction(SanctionType.TEMPORARY,
                        LocalDateTime.of(2026, 9, 1, 10, 0)));

        Document document = restrictedPage();

        assertThat(document.selectFirst(".restricted-badge").text()).isEqualTo("기간제한");
        assertThat(document.select(".restricted-detail dd").eachText())
                .contains("기간제한", "이용약관 위반", "2026-08-15 09:00", "2026-09-01 10:00");
        assertThat(document.select(".restricted-logout form, form[action='/logout']")
                .isEmpty()).isFalse();
    }

    @Test
    void permanentRestrictionIsLabelledInsteadOfShowingAnEndDate() throws Exception {
        when(userSanctionService.getActiveSanction(5L))
                .thenReturn(sanction(SanctionType.PERMANENT, null));

        Document document = restrictedPage();

        assertThat(document.selectFirst(".restricted-badge").text()).isEqualTo("영구제한");
        assertThat(document.selectFirst(".restricted-badge").hasClass("is-permanent")).isTrue();
        assertThat(document.selectFirst(".restricted-permanent").text())
                .isEqualTo("영구 이용제한");
    }

    @Test
    void restrictedMemberSeesTheAppealForm() throws Exception {
        when(userSanctionService.getActiveSanction(5L))
                .thenReturn(sanction(SanctionType.PERMANENT, null));

        Document document = restrictedPage();

        var form = document.selectFirst(".restricted-appeal-form");
        assertThat(form.attr("action")).isEqualTo("/account/restricted/appeals");
        assertThat(form.attr("method")).isEqualToIgnoringCase("post");
        assertThat(form.selectFirst("textarea[name=content]").hasAttr("required")).isTrue();
        assertThat(form.select("button[type=submit]").text()).isEqualTo("이의제기 신청");
        // 대상 제재를 클라이언트가 지정할 수 없어야 한다
        assertThat(form.select("input[name=sanctionId], input[name=userId]")).isEmpty();
    }

    @Test
    void submittedAppealIsForwardedToTheServiceAndRedirectsBack() throws Exception {
        mockMvc.perform(post("/account/restricted/appeals")
                        .with(user(member())).with(csrf())
                        .param("content", "제재 사유에 오해가 있습니다."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/restricted?appealed=true"));

        verify(userAppealService).submit(5L, "제재 사유에 오해가 있습니다.");
    }

    @Test
    void pendingAppealShowsTheReceiptStateInsteadOfTheForm() throws Exception {
        when(userSanctionService.getActiveSanction(5L))
                .thenReturn(sanction(SanctionType.TEMPORARY, LocalDateTime.of(2026, 9, 1, 10, 0)));
        when(userAppealService.getLatestAppeal(10L)).thenReturn(pendingAppeal());

        Document document = restrictedPage();

        assertThat(document.selectFirst(".restricted-appeal-status-title").text())
                .isEqualTo("이의제기가 접수되었습니다.");
        assertThat(document.select(".restricted-appeal-meta dd").eachText())
                .contains("접수됨", "2026-08-15 12:00");
        assertThat(document.selectFirst(".restricted-appeal-content").text())
                .isEqualTo("이미 제출한 내용");
        assertThat(document.select(".restricted-appeal-form")).isEmpty();
    }

    @Test
    void rejectedSubmissionRendersThePageWithTheMessage() throws Exception {
        when(userSanctionService.getActiveSanction(5L))
                .thenReturn(sanction(SanctionType.PERMANENT, null));
        doThrow(new AppealValidationException(null, "이미 접수된 이의제기가 처리 중입니다."))
                .when(userAppealService).submit(eq(5L), any());

        var result = mockMvc.perform(post("/account/restricted/appeals")
                        .with(user(member())).with(csrf())
                        .param("content", "소명합니다"))
                .andExpect(status().isOk())
                .andExpect(view().name("account/restricted"))
                .andReturn();

        assertThat(Jsoup.parse(result.getResponse().getContentAsString())
                .selectFirst(".restricted-alert").text())
                .isEqualTo("이미 접수된 이의제기가 처리 중입니다.");
    }

    @Test
    void appealSubmissionRequiresLoginAndCsrf() throws Exception {
        mockMvc.perform(post("/account/restricted/appeals")
                        .with(user(member()))
                        .param("content", "소명합니다"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/account/restricted/appeals").with(csrf())
                        .param("content", "소명합니다"))
                .andExpect(status().is3xxRedirection());

        verify(userAppealService, org.mockito.Mockito.never()).submit(anyLong(), any());
    }

    @Test
    void anonymousVisitorIsSentToLogin() throws Exception {
        mockMvc.perform(get("/account/restricted"))
                .andExpect(status().is3xxRedirection());
    }

    private Document restrictedPage() throws Exception {
        var result = mockMvc.perform(get("/account/restricted").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("account/restricted"))
                .andReturn();
        return Jsoup.parse(result.getResponse().getContentAsString());
    }

    private CustomUserDetails member() {
        User user = new User();
        user.setId(5L);
        user.setUsername("travler");
        user.setUserPassword("encoded");
        user.setUserRole(UserRole.USER);
        return new CustomUserDetails(user);
    }

    private UserAppeal pendingAppeal() {
        UserAppeal appeal = new UserAppeal();
        appeal.setId(30L);
        appeal.setSanctionId(10L);
        appeal.setUserId(5L);
        appeal.setStatus(AppealStatus.PENDING);
        appeal.setContent("이미 제출한 내용");
        appeal.setSubmittedAt(LocalDateTime.of(2026, 8, 15, 12, 0));
        return appeal;
    }

    private UserSanction sanction(SanctionType type, LocalDateTime expiresAt) {
        UserSanction sanction = new UserSanction();
        sanction.setId(10L);
        sanction.setUserId(5L);
        sanction.setType(type);
        sanction.setStatus(SanctionStatus.ACTIVE);
        sanction.setReason("이용약관 위반");
        sanction.setStartsAt(LocalDateTime.of(2026, 8, 15, 9, 0));
        sanction.setExpiresAt(expiresAt);
        return sanction;
    }
}