package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.model.SanctionStatus;
import com.example.travlediary.model.SanctionType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserSanction;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void appealPlaceholderIsShownWithoutAnyAppealAction() throws Exception {
        when(userSanctionService.getActiveSanction(5L))
                .thenReturn(sanction(SanctionType.PERMANENT, null));

        Document document = restrictedPage();

        assertThat(document.selectFirst(".restricted-appeal-note").text())
                .contains("이의제기");
        assertThat(document.select("a[href*='appeal'], form[action*='appeal']")).isEmpty();
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