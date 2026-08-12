package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.AccountDetailsDto;
import com.example.travlediary.dto.PasswordChangeForm;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AccountReauthenticationService;
import com.example.travlediary.service.user.AccountValidationException;
import com.example.travlediary.service.user.MyPageAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

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
                "/mypage/account/withdraw"}) {
            mockMvc.perform(post(url).with(user(principal)))
                    .andExpect(status().isForbidden());
        }
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
}
