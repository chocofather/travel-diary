package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.AccountDetailsDto;
import com.example.travlediary.dto.PasswordChangeForm;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AccountReauthenticationService;
import com.example.travlediary.service.user.AccountValidationException;
import com.example.travlediary.service.user.MyPageAccountService;
import com.example.travlediary.service.user.SocialAccountService;
import com.example.travlediary.service.user.SocialWithdrawalException;
import com.example.travlediary.service.user.SocialWithdrawalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MyPageAccountController.class)
@Import({SecurityConfig.class, AccountReauthenticationService.class})
class MyPageAccountControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountReauthenticationService reauthenticationService;

    @MockitoBean private MyPageAccountService accountService;
    @MockitoBean private SocialAccountService socialAccountService;
    @MockitoBean private SocialWithdrawalService socialWithdrawalService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @BeforeEach
    void localPasswordIsAvailableByDefault() {
        lenient().when(accountService.hasLocalPassword(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        lenient().when(userMapper.hasLocalPasswordById(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
    }

    @Test
    void guestCannotAccessAnyAccountEndpoint() throws Exception {
        mockMvc.perform(get("/mypage/account"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/mypage/account/edit"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/mypage/account/verify-password"))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountMutationsRequireCsrf() throws Exception {
        CustomUserDetails principal = principal(7L, UserRole.USER);

        for (String url : new String[]{
                "/mypage/account/verify-password",
                "/mypage/account/edit",
                "/mypage/account/password",
                "/mypage/account/withdraw",
                "/mypage/account/social-withdrawal",
                "/mypage/account/social-withdrawal/cancel"}) {
            mockMvc.perform(post(url).with(user(principal)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void localPasswordMemberStillOpensTheExistingVerificationForm() throws Exception {
        mockMvc.perform(get("/mypage/account")
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/account-verify"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "회원정보 보호를 위해 현재 비밀번호를 다시 입력해주세요.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"currentPassword\"")));
    }

    @Test
    void socialOnlyMemberOpensReadOnlyAccountManagementForEveryConnectedProvider()
            throws Exception {
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        when(userMapper.hasLocalPasswordById(77L)).thenReturn(false);
        List<SocialAccount> accounts = List.of(
                socialAccount(77L, SocialProvider.GOOGLE, "google-sub", "google@example.com"),
                socialAccount(77L, SocialProvider.KAKAO, "kakao-sub", null),
                socialAccount(77L, SocialProvider.NAVER, "naver-id", "naver@example.com"));
        when(socialAccountService.findAllByUserId(77L)).thenReturn(accounts);

        mockMvc.perform(get("/mypage/account")
                        .param("userId", "999")
                        .with(user(socialPrincipal(77L))))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/account-social"))
                .andExpect(model().attribute("socialAccounts", accounts))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("계정 관리")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "로그인 계정 정보를 확인할 수 있습니다.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("로그인 계정")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Google")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("카카오")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("네이버")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "google@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "naver@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "이메일 정보 없음")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("name=\"currentPassword\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Travel Diary 비밀번호"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("추후 제공될 예정입니다"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("google-sub"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("kakao-sub"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("naver-id"))));

        verify(socialAccountService).findAllByUserId(77L);
        verify(socialAccountService, never()).findAllByUserId(999L);
    }

    @Test
    void socialOnlyAccountPageShowsASeparateNonImmediateWithdrawalAction()
            throws Exception {
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        when(userMapper.hasLocalPasswordById(77L)).thenReturn(false);
        when(socialAccountService.findAllByUserId(77L)).thenReturn(List.of(
                socialAccount(77L, SocialProvider.NAVER, "hidden-id", null)));

        mockMvc.perform(get("/mypage/account").with(user(socialPrincipal(77L))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("회원 탈퇴")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "mypage-social-withdrawal-summary")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "is-compact-danger")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/mypage/account/social-withdrawal\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("hidden-id"))));

        verify(accountService, never()).withdrawAfterSocialReauthentication(77L);
    }

    @Test
    void socialWithdrawalStartUsesOnlyPrincipalIdAndStoresServerPendingIntent()
            throws Exception {
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        PendingSocialWithdrawal pending = pending(77L, SocialProvider.KAKAO);
        when(socialWithdrawalService.begin(77L)).thenReturn(pending);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/mypage/account/social-withdrawal")
                        .param("userId", "999")
                        .param("provider", "GOOGLE")
                        .session(session)
                        .with(user(socialPrincipal(77L)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/mypage/account/social-withdrawal/confirm"));

        assertThat(session.getAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE))
                .isEqualTo(pending);
        verify(socialWithdrawalService).begin(77L);
        verify(socialWithdrawalService, never()).begin(999L);
    }

    @Test
    void confirmationUsesPendingProviderWithoutExposingItsIdentity() throws Exception {
        PendingSocialWithdrawal pending = pending(77L, SocialProvider.NAVER);
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        when(socialWithdrawalService.isValid(pending, 77L)).thenReturn(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE, pending);

        mockMvc.perform(get("/mypage/account/social-withdrawal/confirm")
                        .session(session)
                        .with(user(socialPrincipal(77L))))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/social-withdrawal-confirm"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "탈퇴 시 영향")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "본인 확인 계정")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "네이버로 본인 확인")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/oauth2/authorization/naver\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/mypage/account/social-withdrawal/cancel\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("providerUserId"))));
    }

    @Test
    void expiredConfirmationConsumesIntentAndReturnsToAccount() throws Exception {
        PendingSocialWithdrawal pending = pending(77L, SocialProvider.GOOGLE);
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        when(socialWithdrawalService.isValid(pending, 77L)).thenReturn(false);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE, pending);

        mockMvc.perform(get("/mypage/account/social-withdrawal/confirm")
                        .session(session)
                        .with(user(socialPrincipal(77L))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"));

        assertThat(session.getAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void invalidOrMultipleProviderWithdrawalClearsIntentAndChangesNothing()
            throws Exception {
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        when(socialWithdrawalService.begin(77L)).thenThrow(
                new SocialWithdrawalException(
                        "여러 로그인 수단이 연결된 계정은 현재 탈퇴를 처리할 수 없습니다."));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE,
                pending(77L, SocialProvider.GOOGLE));

        mockMvc.perform(post("/mypage/account/social-withdrawal")
                        .session(session)
                        .with(user(socialPrincipal(77L)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("socialWithdrawalError",
                                "여러 로그인 수단이 연결된 계정은 현재 탈퇴를 처리할 수 없습니다."));

        assertThat(session.getAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE)).isNull();
        verify(accountService, never()).withdrawAfterSocialReauthentication(77L);
    }

    @Test
    void cancellingConfirmationConsumesThePendingIntent() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE,
                pending(77L, SocialProvider.GOOGLE));

        mockMvc.perform(post("/mypage/account/social-withdrawal/cancel")
                        .session(session)
                        .with(user(socialPrincipal(77L)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"));

        assertThat(session.getAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void socialOnlyMemberCannotEnterAnyLocalPasswordAccountEndpointDirectly()
            throws Exception {
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        MockHttpSession session = new MockHttpSession();
        reauthenticationService.markVerified(session, 77L);
        CustomUserDetails principal = socialPrincipal(77L);

        mockMvc.perform(get("/mypage/account/edit")
                        .session(session).with(user(principal)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"));
        mockMvc.perform(post("/mypage/account/verify-password")
                        .session(session).with(user(principal)).with(csrf())
                        .param("currentPassword", "attacker-value"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"));
        mockMvc.perform(post("/mypage/account/edit")
                        .session(session).with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"));
        mockMvc.perform(post("/mypage/account/password")
                        .session(session).with(user(principal)).with(csrf())
                        .param("newPassword", "NewPassword!")
                        .param("newPasswordConfirm", "NewPassword!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"));
        mockMvc.perform(post("/mypage/account/withdraw")
                        .session(session).with(user(principal)).with(csrf())
                        .param("currentPassword", "attacker-value"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"));

        verify(accountService, never()).verifyCurrentPassword(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(accountService, never()).updateAccountDetails(
                org.mockito.ArgumentMatchers.anyLong(), any());
        verify(accountService, never()).changePassword(
                org.mockito.ArgumentMatchers.anyLong(), any());
        verify(accountService, never()).withdraw(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void correctPasswordMarksTheCurrentUserAsVerified() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(accountService.verifyCurrentPassword(7L, "Password!")).thenReturn(true);

        mockMvc.perform(post("/mypage/account/verify-password")
                        .session(session)
                        .with(user(principal(7L, UserRole.USER)))
                        .with(csrf())
                        .param("currentPassword", "Password!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account/edit"));

        assertThat(reauthenticationService.isVerified(session, 7L)).isTrue();
    }

    @Test
    void wrongPasswordReturnsTheVerificationFormWithoutEchoingPassword() throws Exception {
        when(accountService.verifyCurrentPassword(7L, "wrong")).thenReturn(false);

        mockMvc.perform(post("/mypage/account/verify-password")
                        .with(user(principal(7L, UserRole.USER)))
                        .with(csrf())
                        .param("currentPassword", "wrong"))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/account-verify"))
                .andExpect(model().attributeHasFieldErrors(
                        "verifyForm", "currentPassword"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("value=\"wrong\""))));
    }

    @Test
    void directEditAccessWithoutRecentVerificationRedirects() throws Exception {
        mockMvc.perform(get("/mypage/account/edit")
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"));

        verify(accountService, never()).getAccountDetails(7L);
    }

    @Test
    void userAndAdminCanOpenVerifiedEditPageUsingPrincipalId() throws Exception {
        AccountDetailsDto member = details("member", "member@example.com");
        AccountDetailsDto admin = details("admin", "admin@example.com");
        when(accountService.getAccountDetails(7L)).thenReturn(member);
        when(accountService.getAccountDetails(99L)).thenReturn(admin);

        MockHttpSession memberSession = new MockHttpSession();
        reauthenticationService.markVerified(memberSession, 7L);
        mockMvc.perform(get("/mypage/account/edit")
                        .session(memberSession)
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/account-edit"))
                .andExpect(model().attribute("account", member));

        MockHttpSession adminSession = new MockHttpSession();
        reauthenticationService.markVerified(adminSession, 99L);
        mockMvc.perform(get("/mypage/account/edit")
                        .session(adminSession)
                        .with(user(principal(99L, UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("account", admin))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "관리자 계정은 마이페이지에서 탈퇴할 수 없습니다.")));
    }

    @Test
    void detailsUpdateUsesPrincipalIdAndKeepsVerification() throws Exception {
        MockHttpSession session = new MockHttpSession();
        reauthenticationService.markVerified(session, 7L);

        mockMvc.perform(post("/mypage/account/edit")
                        .session(session)
                        .with(user(principal(7L, UserRole.USER)))
                        .with(csrf())
                        .param("fullName", "여행 민준")
                        .param("userPhone", "010-1234-5678")
                        .param("userBirth", "2000-01-02")
                        .param("userId", "999")
                        .param("userEmail", "attacker@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account/edit"));

        verify(accountService).updateAccountDetails(eq(7L), any());
        assertThat(reauthenticationService.isVerified(session, 7L)).isTrue();
    }

    @Test
    void successfulPasswordChangeInvalidatesCurrentSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        reauthenticationService.markVerified(session, 7L);

        mockMvc.perform(post("/mypage/account/password")
                        .session(session)
                        .with(user(principal(7L, UserRole.USER)))
                        .with(csrf())
                        .param("newPassword", "NewPassword!")
                        .param("newPasswordConfirm", "NewPassword!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?passwordChanged=true"));

        verify(accountService).changePassword(eq(7L), any(PasswordChangeForm.class));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void adminWithdrawalErrorDoesNotInvalidateSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        reauthenticationService.markVerified(session, 99L);
        when(accountService.getAccountDetails(99L))
                .thenReturn(details("admin", "admin@example.com"));
        doThrow(new AccountValidationException(
                null, "관리자 계정은 마이페이지에서 탈퇴할 수 없습니다."))
                .when(accountService).withdraw(99L, "Password!");

        mockMvc.perform(post("/mypage/account/withdraw")
                        .session(session)
                        .with(user(principal(99L, UserRole.ADMIN)))
                        .with(csrf())
                        .param("currentPassword", "Password!"))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/account-edit"));

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void successfulUserWithdrawalInvalidatesSessionAfterServiceReturns() throws Exception {
        MockHttpSession session = new MockHttpSession();
        reauthenticationService.markVerified(session, 7L);

        mockMvc.perform(post("/mypage/account/withdraw")
                        .session(session)
                        .with(user(principal(7L, UserRole.USER)))
                        .with(csrf())
                        .param("currentPassword", "Password!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?withdrawn=true"));

        verify(accountService).withdraw(7L, "Password!");
        assertThat(session.isInvalid()).isTrue();
    }

    private AccountDetailsDto details(String username, String email) {
        AccountDetailsDto details = new AccountDetailsDto();
        details.setUsername(username);
        details.setUserEmail(email);
        details.setFullName("여행 민준");
        details.setUserPhone("010-1234-5678");
        details.setUserBirth(LocalDate.of(2000, 1, 2));
        return details;
    }

    private CustomUserDetails principal(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername("member" + id);
        user.setUserPassword("encoded-password");
        user.setUserRole(role);
        return new CustomUserDetails(user);
    }

    private CustomUserDetails socialPrincipal(Long id) {
        User user = new User();
        user.setId(id);
        user.setUserRole(UserRole.USER);
        return new CustomUserDetails(user);
    }

    private SocialAccount socialAccount(Long userId, SocialProvider provider,
                                        String providerUserId, String providerEmail) {
        SocialAccount account = new SocialAccount();
        account.setUserId(userId);
        account.setProvider(provider);
        account.setProviderUserId(providerUserId);
        account.setProviderEmail(providerEmail);
        return account;
    }

    private PendingSocialWithdrawal pending(Long userId, SocialProvider provider) {
        Instant now = Instant.now();
        return new PendingSocialWithdrawal(
                "flow-id", userId, provider, now, now.plusSeconds(600));
    }
}
