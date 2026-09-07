package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.I18nConfig;
import com.example.travlediary.config.i18n.TravelDiaryLocaleResolver;
import com.example.travlediary.dto.AccountDetailsDto;
import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AccountReauthenticationService;
import com.example.travlediary.service.user.MyPageAccountService;
import com.example.travlediary.service.user.SocialAccountService;
import com.example.travlediary.service.user.SocialWithdrawalException;
import com.example.travlediary.service.user.SocialWithdrawalService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계정 관리 화면의 고정 문구가 요청 언어를 따르는지 본다.
 * 사용자 데이터와 provider 식별값은 언어와 무관하게 그대로여야 한다.
 */
@WebMvcTest(MyPageAccountController.class)
@Import({SecurityConfig.class, I18nConfig.class, AccountReauthenticationService.class})
class MyPageAccountLocaleRenderingTest {

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
        lenient().when(accountService.hasLocalPassword(anyLong())).thenReturn(true);
        lenient().when(userMapper.hasLocalPasswordById(anyLong())).thenReturn(true);
    }

    @Test
    void englishVerifyScreenLocalizesItsLabels() throws Exception {
        mockMvc.perform(get("/mypage/account").with(user(principal(7L, UserRole.USER)))
                        .cookie(english()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "To protect your account, please enter your current password again.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Current password")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirm")));
    }

    @Test
    void englishEditScreenLocalizesLabelsAndKeepsTheAccountValues() throws Exception {
        MockHttpSession session = new MockHttpSession();
        reauthenticationService.markVerified(session, 7L);
        when(accountService.getAccountDetails(7L))
                .thenReturn(details("minjun", "member@example.com"));

        mockMvc.perform(get("/mypage/account/edit").session(session)
                        .with(user(principal(7L, UserRole.USER)))
                        .cookie(english()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Manage your account and personal details securely.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sign-in ID")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Change password")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Delete account")))
                // 로그인 ID·이메일·이름 같은 사용자 데이터는 번역 대상이 아니다
                .andExpect(content().string(org.hamcrest.Matchers.containsString("minjun")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "member@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("여행 민준")));
    }

    @Test
    void japaneseSocialScreenLocalizesLabelsAndKeepsProviderIdentity() throws Exception {
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        when(socialAccountService.findAllByUserId(77L))
                .thenReturn(List.of(socialAccount(SocialProvider.KAKAO, "social@example.com")));

        mockMvc.perform(get("/mypage/account").with(user(principal(77L, UserRole.USER)))
                        .cookie(new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, "ja")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "ログインに使用しているアカウント情報を確認できます。")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "ソーシャルアカウントのメール")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("退会する")))
                // 브랜드 이름과 provider 가 준 이메일은 그대로 둔다
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kakao")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "social@example.com")));
    }

    @Test
    void englishWithdrawalConfirmationLocalizesTheProviderPrompt() throws Exception {
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        PendingSocialWithdrawal pending = pending(77L, SocialProvider.NAVER);
        when(socialWithdrawalService.isValid(pending, 77L)).thenReturn(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE, pending);

        mockMvc.perform(get("/mypage/account/social-withdrawal/confirm").session(session)
                        .with(user(principal(77L, UserRole.USER)))
                        .cookie(english()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "What happens when you leave")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Once deleted, your account cannot be restored.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Verify with Naver")))
                // 인증 경로는 provider enum 으로 만들어지므로 언어와 무관하다
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/oauth2/authorization/naver")));
    }

    @Test
    void englishWithdrawalFailureFlashUsesTheRequestedLanguage() throws Exception {
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        when(socialWithdrawalService.begin(77L)).thenThrow(new SocialWithdrawalException(
                "mypage.account.error.social.multipleProviders",
                "여러 로그인 수단이 연결된 계정은 현재 탈퇴를 처리할 수 없습니다."));

        mockMvc.perform(post("/mypage/account/social-withdrawal")
                        .with(user(principal(77L, UserRole.USER)))
                        .with(csrf())
                        .cookie(english()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("socialWithdrawalError",
                        "Accounts with several sign-in methods cannot be deleted yet."));
    }

    @Test
    void englishPasswordVerificationErrorUsesTheRequestedLanguage() throws Exception {
        when(accountService.verifyCurrentPassword(7L, "wrong")).thenReturn(false);

        mockMvc.perform(post("/mypage/account/verify-password")
                        .with(user(principal(7L, UserRole.USER)))
                        .with(csrf())
                        .cookie(english())
                        .param("currentPassword", "wrong"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "The password does not match.")));
    }

    private Cookie english() {
        return new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, "en");
    }

    private AccountDetailsDto details(String username, String email) {
        AccountDetailsDto dto = new AccountDetailsDto();
        dto.setUsername(username);
        dto.setUserEmail(email);
        dto.setFullName("여행 민준");
        dto.setUserPhone("010-1234-5678");
        dto.setUserBirth(LocalDate.of(2000, 1, 2));
        return dto;
    }

    private SocialAccount socialAccount(SocialProvider provider, String email) {
        SocialAccount account = new SocialAccount();
        account.setUserId(77L);
        account.setProvider(provider);
        account.setProviderUserId("provider-user-id");
        account.setProviderEmail(email);
        return account;
    }

    private PendingSocialWithdrawal pending(Long userId, SocialProvider provider) {
        Instant now = Instant.now();
        return new PendingSocialWithdrawal(
                "intent", userId, provider, now, now.plusSeconds(300));
    }

    private CustomUserDetails principal(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername("member");
        user.setUserPassword("password");
        user.setUserRole(role);
        return new CustomUserDetails(user);
    }
}
