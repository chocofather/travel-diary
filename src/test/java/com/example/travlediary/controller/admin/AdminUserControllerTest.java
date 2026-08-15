package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.AdminUserDetailDto;
import com.example.travlediary.dto.AdminUserListItemDto;
import com.example.travlediary.dto.SanctionReleaseForm;
import com.example.travlediary.dto.UserSanctionForm;
import com.example.travlediary.model.SanctionStatus;
import com.example.travlediary.model.SanctionType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserSanction;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AdminUserService;
import com.example.travlediary.service.user.SanctionValidationException;
import com.example.travlediary.service.user.UserSanctionService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;
    @MockitoBean
    private UserSanctionService userSanctionService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void listRendersAccountColumnsAndDetailLink() throws Exception {
        when(adminUserService.countUsers(null, null)).thenReturn(1L);
        when(adminUserService.getUsers(null, null, 0L, 20)).thenReturn(List.of(listItem()));

        Document document = adminPage("/admin/users");

        assertThat(document.select(".admin-users-table thead th").eachText())
                .containsExactly("아이디", "닉네임", "이메일", "권한", "회원 상태", "가입일", "관리");
        assertThat(document.selectFirst(".admin-user-username a").text()).isEqualTo("travler");
        assertThat(document.selectFirst(".admin-user-email").text()).isEqualTo("user@example.com");
        assertThat(document.selectFirst(".admin-user-status").text()).isEqualTo("정상");
        assertThat(document.selectFirst(".admin-user-status").hasClass("is-active")).isTrue();
        assertThat(document.select(".admin-users-table tbody a").eachAttr("href"))
                .contains("/admin/users/7");
    }

    @Test
    void listShowsAdminAccountsWithAnAdminRoleBadge() throws Exception {
        AdminUserListItemDto admin = listItem();
        admin.setId(1L);
        admin.setUsername("master");
        admin.setUserRole(UserRole.ADMIN);
        when(adminUserService.countUsers(null, null)).thenReturn(2L);
        when(adminUserService.getUsers(null, null, 0L, 20))
                .thenReturn(List.of(admin, listItem()));

        Document document = adminPage("/admin/users");

        // 관리자 계정도 목록에는 그대로 노출된다
        assertThat(document.select(".admin-users-table tbody .admin-user-username a").eachText())
                .containsExactly("master", "travler");
        assertThat(document.select(".admin-users-table tbody .admin-user-role").eachText())
                .containsExactly("관리자", "일반회원");
        assertThat(document.select(".admin-users-table tbody .admin-user-role.is-admin"))
                .hasSize(1);
    }

    @Test
    void roleIsAvailableForLaterSanctionRulesOnBothViews() throws Exception {
        AdminUserListItemDto admin = listItem();
        admin.setUserRole(UserRole.ADMIN);
        assertThat(admin.isAdmin()).isTrue();
        assertThat(listItem().isAdmin()).isFalse();

        AdminUserDetailDto adminDetail = detail();
        adminDetail.setUserRole(UserRole.ADMIN);
        when(adminUserService.getUser(7L)).thenReturn(adminDetail);

        Document document = adminPage("/admin/users/7");

        assertThat(adminDetail.isAdmin()).isTrue();
        assertThat(document.selectFirst(".admin-user-role").text()).isEqualTo("관리자");
        assertThat(document.selectFirst(".admin-user-role").hasClass("is-admin")).isTrue();
        assertThat(document.selectFirst(".admin-user-role-value").text())
                .isEqualTo("관리자 (ADMIN)");
    }

    @Test
    void searchKeywordAndStatusFilterAreForwardedToTheService() throws Exception {
        when(adminUserService.countUsers("여행", UserStatus.RESTRICTED)).thenReturn(0L);
        when(adminUserService.getUsers("여행", UserStatus.RESTRICTED, 0L, 20))
                .thenReturn(List.of());

        mockMvc.perform(get("/admin/users")
                        .param("keyword", "  여행  ")
                        .param("status", "RESTRICTED")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/list"))
                .andExpect(model().attribute("keyword", "여행"))
                .andExpect(model().attribute("currentStatus", "RESTRICTED"));

        verify(adminUserService).getUsers("여행", UserStatus.RESTRICTED, 0L, 20);
    }

    @Test
    void blankKeywordAndUnknownStatusFallBackToUnfilteredSearch() throws Exception {
        when(adminUserService.countUsers(null, null)).thenReturn(0L);
        when(adminUserService.getUsers(null, null, 0L, 20)).thenReturn(List.of());

        mockMvc.perform(get("/admin/users")
                        .param("keyword", "   ")
                        .param("status", "UNKNOWN")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentStatus", "ALL"));

        verify(adminUserService).getUsers(null, null, 0L, 20);
    }

    @Test
    void pageParameterMovesTheOffsetAndIsClampedToTheLastPage() throws Exception {
        when(adminUserService.countUsers(null, null)).thenReturn(45L);
        when(adminUserService.getUsers(eq(null), eq(null), anyLong(), anyInt()))
                .thenReturn(List.of(listItem()));

        mockMvc.perform(get("/admin/users").param("page", "2")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("totalPages", 3));
        verify(adminUserService).getUsers(null, null, 20L, 20);

        mockMvc.perform(get("/admin/users").param("page", "99")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 3));
        verify(adminUserService).getUsers(null, null, 40L, 20);
    }

    @Test
    void emptyListShowsEmptyMessageWithoutPagination() throws Exception {
        when(adminUserService.countUsers(null, null)).thenReturn(0L);
        when(adminUserService.getUsers(null, null, 0L, 20)).thenReturn(List.of());

        Document document = adminPage("/admin/users");

        assertThat(document.selectFirst(".admin-table-empty").text())
                .isEqualTo("조회된 회원이 없습니다.");
        assertThat(document.select(".admin-user-pagination")).isEmpty();
    }

    @Test
    void detailShowsAccountInformation() throws Exception {
        AdminUserDetailDto user = detail();
        user.setStatus(UserStatus.ACTIVE);
        when(adminUserService.getUser(7L)).thenReturn(user);

        Document document = adminPage("/admin/users/7");

        assertThat(document.selectFirst(".admin-page-title").text()).isEqualTo("회원 상세");
        assertThat(document.select(".admin-user-meta dd").eachText())
                .contains("travler", "여행자", "user@example.com", "홍길동", "2000-05-04");
        assertThat(document.selectFirst(".admin-user-status").text()).isEqualTo("정상");
    }

    /* === 이용제한 === */

    @Test
    void normalUserDetailShowsTheRestrictionFormOnly() throws Exception {
        AdminUserDetailDto user = detail();
        user.setStatus(UserStatus.ACTIVE);
        when(adminUserService.getUser(7L)).thenReturn(user);
        when(userSanctionService.getActiveSanction(7L)).thenReturn(null);

        Document document = adminPage("/admin/users/7");

        assertThat(document.selectFirst("#admin-user-sanction-form").attr("action"))
                .isEqualTo("/admin/users/7/restrict");
        assertThat(document.select("input[name=type][value=TEMPORARY]")).hasSize(1);
        assertThat(document.select("input[name=type][value=PERMANENT]")).hasSize(1);
        assertThat(document.selectFirst("#sanction-reason").hasAttr("required")).isTrue();
        assertThat(document.select("form[action='/admin/users/7/release']")).isEmpty();
    }

    @Test
    void restrictedUserDetailShowsSanctionDetailsAndReleaseForm() throws Exception {
        when(adminUserService.getUser(7L)).thenReturn(detail());
        when(userSanctionService.getActiveSanction(7L)).thenReturn(activeSanction());

        Document document = adminPage("/admin/users/7");

        assertThat(document.select(".admin-user-sanction-meta dd").eachText())
                .contains("기간제한", "2026-09-01 10:00", "이용약관 위반");
        assertThat(document.select("form[action='/admin/users/7/release']")).hasSize(1);
        assertThat(document.select("#admin-user-sanction-form")).isEmpty();
    }

    @Test
    void adminAccountDetailOffersNoSanctionForm() throws Exception {
        AdminUserDetailDto adminUser = detail();
        adminUser.setUserRole(UserRole.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);
        when(adminUserService.getUser(7L)).thenReturn(adminUser);

        Document document = adminPage("/admin/users/7");

        assertThat(document.selectFirst(".admin-user-sanction-guide").text())
                .isEqualTo("관리자 계정은 이용제한 대상이 아닙니다.");
        assertThat(document.select("#admin-user-sanction-form")).isEmpty();
        assertThat(document.select("form[action='/admin/users/7/release']")).isEmpty();
    }

    @Test
    void restrictPostPassesTheFormAndAdminIdToTheService() throws Exception {
        mockMvc.perform(post("/admin/users/7/restrict")
                        .with(user(admin())).with(csrf())
                        .param("type", "TEMPORARY")
                        .param("reason", "이용약관 위반")
                        .param("adminNote", "내부 메모")
                        .param("expiresAt", "2026-09-01T10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/7"));

        ArgumentCaptor<UserSanctionForm> captor =
                ArgumentCaptor.forClass(UserSanctionForm.class);
        verify(userSanctionService).restrict(eq(7L), captor.capture(), eq(1L));
        UserSanctionForm form = captor.getValue();
        assertThat(form.getType()).isEqualTo(SanctionType.TEMPORARY);
        assertThat(form.getReason()).isEqualTo("이용약관 위반");
        assertThat(form.getAdminNote()).isEqualTo("내부 메모");
        assertThat(form.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 0));
    }

    @Test
    void releasePostPassesTheReasonAndAdminIdToTheService() throws Exception {
        mockMvc.perform(post("/admin/users/7/release")
                        .with(user(admin())).with(csrf())
                        .param("releaseReason", "소명 완료"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/7"));

        ArgumentCaptor<SanctionReleaseForm> captor =
                ArgumentCaptor.forClass(SanctionReleaseForm.class);
        verify(userSanctionService).release(eq(7L), captor.capture(), eq(1L));
        assertThat(captor.getValue().getReleaseReason()).isEqualTo("소명 완료");
    }

    @Test
    void rejectedRestrictionRendersTheDetailPageWithTheMessage() throws Exception {
        AdminUserDetailDto user = detail();
        user.setStatus(UserStatus.ACTIVE);
        when(adminUserService.getUser(7L)).thenReturn(user);
        doThrow(new SanctionValidationException(null, "이미 적용 중인 이용제한이 있습니다."))
                .when(userSanctionService).restrict(eq(7L), any(), eq(1L));

        var result = mockMvc.perform(post("/admin/users/7/restrict")
                        .with(user(admin())).with(csrf())
                        .param("type", "PERMANENT")
                        .param("reason", "이용약관 위반"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/detail"))
                .andReturn();

        assertThat(Jsoup.parse(result.getResponse().getContentAsString())
                .selectFirst(".admin-alert").text())
                .isEqualTo("이미 적용 중인 이용제한이 있습니다.");
    }


    @Test
    void sanctionPostsRequireCsrfAndAdminRole() throws Exception {
        mockMvc.perform(post("/admin/users/7/restrict")
                        .with(user(admin()))
                        .param("type", "PERMANENT")
                        .param("reason", "사유"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/users/7/release")
                        .with(user(admin())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/users/7/restrict")
                        .with(user("member").roles("USER")).with(csrf())
                        .param("type", "PERMANENT")
                        .param("reason", "사유"))
                .andExpect(status().isForbidden());

        verify(userSanctionService, org.mockito.Mockito.never())
                .restrict(anyLong(), any(), anyLong());
        verify(userSanctionService, org.mockito.Mockito.never())
                .release(anyLong(), any(), anyLong());
    }

    @Test
    void missingUserReturnsNotFound() throws Exception {
        when(adminUserService.getUser(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/admin/users/99").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void regularUserCannotAccessAdminUserPages() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/users/7").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        verify(adminUserService, org.mockito.Mockito.never()).getUsers(any(), any(), anyLong(), anyInt());
    }

    private Document adminPage(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private AdminUserListItemDto listItem() {
        AdminUserListItemDto item = new AdminUserListItemDto();
        item.setId(7L);
        item.setUsername("travler");
        item.setNickname("여행자");
        item.setUserEmail("user@example.com");
        item.setUserRole(UserRole.USER);
        item.setStatus(UserStatus.ACTIVE);
        item.setCreatedAt(Timestamp.valueOf("2026-08-12 10:00:00"));
        return item;
    }

    private CustomUserDetails admin() {
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("master");
        adminUser.setUserPassword("password");
        adminUser.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(adminUser);
    }

    private UserSanction activeSanction() {
        UserSanction sanction = new UserSanction();
        sanction.setId(10L);
        sanction.setUserId(7L);
        sanction.setType(SanctionType.TEMPORARY);
        sanction.setStatus(SanctionStatus.ACTIVE);
        sanction.setReason("이용약관 위반");
        sanction.setStartsAt(LocalDateTime.of(2026, 8, 15, 9, 0));
        sanction.setExpiresAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        sanction.setCreatedBy(1L);
        return sanction;
    }

    private AdminUserDetailDto detail() {
        AdminUserDetailDto user = new AdminUserDetailDto();
        user.setId(7L);
        user.setUsername("travler");
        user.setNickname("여행자");
        user.setUserEmail("user@example.com");
        user.setFullName("홍길동");
        user.setUserPhone("010-1234-5678");
        user.setUserBirth(LocalDate.of(2000, 5, 4));
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.RESTRICTED);
        user.setCreatedAt(Timestamp.valueOf("2026-08-12 10:00:00"));
        return user;
    }
}