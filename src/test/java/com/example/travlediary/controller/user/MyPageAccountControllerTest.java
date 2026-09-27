package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.AccountDetailsDto;
import com.example.travlediary.dto.PasswordChangeForm;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.PendingSocialConnection;
import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialConnectionNotice;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AccountReauthenticationService;
import com.example.travlediary.service.user.AccountValidationException;
import com.example.travlediary.service.user.MyPageAccountService;
import com.example.travlediary.service.user.SocialAccountService;
import com.example.travlediary.service.user.SocialDisconnectionResult;
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
import static org.mockito.Mockito.times;
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
                "/mypage/account/social-connections/google",
                "/mypage/account/social-connections/google/disconnect",
                "/mypage/account/social-withdrawal",
                "/mypage/account/social-withdrawal/cancel"}) {
            mockMvc.perform(post(url).with(user(principal)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void unsupportedConnectionProviderStillRequiresCsrfBeforeTouchingSession()
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        PendingSocialWithdrawal pending = pending(7L, SocialProvider.GOOGLE);
        session.setAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE, pending);

        mockMvc.perform(post("/mypage/account/social-connections/unsupported")
                        .session(session)
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isForbidden());

        assertThat(session.getAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE))
                .isEqualTo(pending);
    }

    @Test
    void socialConnectionStartUsesPrincipalAndStoresOnlyServerSideIntent()
            throws Exception {
        when(accountService.hasLocalPassword(7L)).thenReturn(false);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 999L);
        session.setAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE,
                pending(7L, SocialProvider.NAVER));

        mockMvc.perform(post("/mypage/account/social-connections/google")
                        .param("userId", "999")
                        .param("provider", "NAVER")
                        .session(session)
                        .with(user(principal(7L, UserRole.USER)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/google"));

        assertThat(session.getAttribute("userId")).isEqualTo(7L);
        assertThat(session.getAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute(PendingSocialConnection.SESSION_ATTRIBUTE))
                .isInstanceOfSatisfying(PendingSocialConnection.class, pending -> {
                    assertThat(pending.userId()).isEqualTo(7L);
                    assertThat(pending.provider()).isEqualTo(SocialProvider.GOOGLE);
                    assertThat(pending.isValidAt(Instant.now())).isTrue();
                });
        verify(socialAccountService).findByUserIdAndProvider(
                7L, SocialProvider.GOOGLE);
        verify(socialAccountService, never()).findByUserIdAndProvider(
                999L, SocialProvider.NAVER);
    }

    @Test
    void localMemberCannotStartConnectionWithoutRecentPasswordVerification()
            throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 비밀번호 확인 화면으로 보내고, 확인이 끝나면 계정 관리 화면으로 돌아온다.
        mockMvc.perform(post("/mypage/account/social-connections/google")
                        .session(session)
                        .with(user(principal(7L, UserRole.USER)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account/verify?next=account"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("verificationMessage",
                                "소셜 계정 연결을 바꾸려면 비밀번호를 한 번 더 확인해 주세요."));
        mockMvc.perform(post("/mypage/account/social-connections/google/disconnect")
                        .session(session)
                        .with(user(principal(7L, UserRole.USER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/mypage/account/verify?next=account"));

        assertThat(session.getAttribute(PendingSocialConnection.SESSION_ATTRIBUTE)).isNull();
        verify(socialAccountService, never()).findByUserIdAndProvider(
                7L, SocialProvider.GOOGLE);
        verify(socialAccountService, never()).disconnectFromUser(any(), any());
    }

    @Test
    void verifiedLocalMemberStartsTheSameOAuthConnectionFlow() throws Exception {
        MockHttpSession session = new MockHttpSession();
        reauthenticationService.markVerified(session, 7L);

        mockMvc.perform(post("/mypage/account/social-connections/naver")
                        .session(session)
                        .with(user(principal(7L, UserRole.USER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/oauth2/authorization/naver"));

        assertThat(session.getAttribute(PendingSocialConnection.SESSION_ATTRIBUTE))
                .isInstanceOfSatisfying(PendingSocialConnection.class, pending -> {
                    assertThat(pending.userId()).isEqualTo(7L);
                    assertThat(pending.provider()).isEqualTo(SocialProvider.NAVER);
                });
    }

    @Test
    void existingProviderConnectionDoesNotCreateAnotherIntent() throws Exception {
        when(accountService.hasLocalPassword(7L)).thenReturn(false);
        MockHttpSession session = new MockHttpSession();
        when(socialAccountService.findByUserIdAndProvider(7L, SocialProvider.KAKAO))
                .thenReturn(socialAccount(7L, SocialProvider.KAKAO, "subject", null));

        mockMvc.perform(post("/mypage/account/social-connections/kakao")
                        .session(session)
                        .with(user(principal(7L, UserRole.USER)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"));

        assertThat(session.getAttribute(PendingSocialConnection.SESSION_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute(SocialConnectionNotice.SESSION_ATTRIBUTE))
                .isEqualTo(new SocialConnectionNotice(
                        SocialConnectionNotice.Type.ALREADY_CONNECTED,
                        SocialProvider.KAKAO));
    }

    /** 일반 회원도 비밀번호 입력 없이 공통 계정 관리 화면에서 소셜 계정 연결 영역을 바로 본다. */
    @Test
    void localPasswordMemberSeesTheCommonAccountPageWithoutEnteringAPassword() throws Exception {
        when(accountService.getAccountDetails(7L)).thenReturn(details("member@example.com"));

        mockMvc.perform(get("/mypage/account")
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/account"))
                .andExpect(model().attribute("localPasswordAccount", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("member@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("소셜 계정 연결")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/mypage/account/social-connections/google\"")))
                // 비밀번호 변경은 별도 메뉴(누르면 비밀번호 확인)로만 보이고, 이 화면에서는 비밀번호를 묻지 않는다.
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/mypage/account/edit\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("변경하기")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("name=\"currentPassword\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("name=\"newPassword\""))))
                // 비밀번호 회원의 탈퇴는 비밀번호 확인 뒤 탈퇴 화면으로, 소셜 인증 탈퇴 폼은 없다.
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/mypage/account/withdraw\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/mypage/account/social-withdrawal"))));
    }

    /** 일반 회원이 소셜 계정도 연결한 경우: 비밀번호 변경 메뉴와 연결 상태·해제 버튼이 함께 보인다. */
    @Test
    void mixedMemberSeesPasswordMenuAndEveryProviderState() throws Exception {
        when(socialAccountService.findAllByUserId(7L)).thenReturn(List.of(
                socialAccount(7L, SocialProvider.KAKAO, "kakao-sub", "kakao@example.com")));

        mockMvc.perform(get("/mypage/account").with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/mypage/account/edit\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("kakao@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/mypage/account/social-connections/kakao/disconnect\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/mypage/account/social-connections/google\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/mypage/account/social-connections/naver\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(
                                "action=\"/mypage/account/social-connections/kakao\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("kakao-sub"))));
    }

    @Test
    void passwordVerificationFormKeepsOnlyAnAllowedDestination() throws Exception {
        mockMvc.perform(get("/mypage/account/verify").param("next", "password")
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/account-verify"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "회원정보 보호를 위해 현재 비밀번호를 다시 입력해주세요.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"currentPassword\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"next\" value=\"password\"")));
        // 목록에 없는 값(외부 주소 등)은 계정 관리로 되돌린다.
        mockMvc.perform(get("/mypage/account/verify").param("next", "https://evil.example")
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"next\" value=\"account\"")));
        // 이미 최근에 확인했으면 바로 목적지로 간다.
        MockHttpSession session = new MockHttpSession();
        reauthenticationService.markVerified(session, 7L);
        mockMvc.perform(get("/mypage/account/verify").param("next", "withdraw").session(session)
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(redirectedUrl("/mypage/account/withdraw"));
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
                .andExpect(view().name("mypage/account"))
                .andExpect(model().attribute("socialAccounts", accounts))
                .andExpect(model().attribute("localPasswordAccount", false))
                // 비밀번호가 없는 소셜 회원에게는 비밀번호 변경 메뉴를 보이지 않는다.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("href=\"/mypage/account/edit\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/mypage/account/social-withdrawal\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("계정 및 보안")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "계정 정보와 로그인 수단을 안전하게 관리합니다.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("소셜 계정 연결")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Google")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("카카오")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("네이버")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("연결됨")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "google@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "naver@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "이메일 정보 없음")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("연결하기"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("name=\"currentPassword\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Tripbora 비밀번호"))))
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
    void accountPageShowsConnectActionsOnlyForUnlinkedProviders() throws Exception {
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        when(userMapper.hasLocalPasswordById(77L)).thenReturn(false);
        when(socialAccountService.findAllByUserId(77L)).thenReturn(List.of(
                socialAccount(77L, SocialProvider.GOOGLE, "google-sub", null)));

        mockMvc.perform(get("/mypage/account").with(user(socialPrincipal(77L))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/mypage/account/social-connections/kakao\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/mypage/account/social-connections/naver\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(
                                "action=\"/mypage/account/social-connections/google\""))));
    }

    @Test
    void connectionNoticeIsShownOnceAndRemovedFromSession() throws Exception {
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        when(userMapper.hasLocalPasswordById(77L)).thenReturn(false);
        when(socialAccountService.findAllByUserId(77L)).thenReturn(List.of(
                socialAccount(77L, SocialProvider.GOOGLE, "google-sub", null)));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SocialConnectionNotice.SESSION_ATTRIBUTE,
                new SocialConnectionNotice(
                        SocialConnectionNotice.Type.CONNECTED, SocialProvider.GOOGLE));

        mockMvc.perform(get("/mypage/account")
                        .session(session)
                        .with(user(socialPrincipal(77L))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Google 계정이 연결되었습니다.")));

        assertThat(session.getAttribute(SocialConnectionNotice.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void connectedProviderShowsStatusDisconnectActionAndConfirmation() throws Exception {
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        when(userMapper.hasLocalPasswordById(77L)).thenReturn(false);
        when(socialAccountService.findAllByUserId(77L)).thenReturn(List.of(
                socialAccount(77L, SocialProvider.GOOGLE, "hidden-subject", null)));

        mockMvc.perform(get("/mypage/account").with(user(socialPrincipal(77L))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("연결됨")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("연결 해제")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/mypage/account/social-connections/google/disconnect\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Google 계정 연결을 해제하시겠습니까? 해제 후에는 해당 계정으로 로그인할 수 없습니다.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("hidden-subject"))));
    }

    @Test
    void disconnectUsesAuthenticatedUserIdAndStoresSuccessNotice() throws Exception {
        when(accountService.hasLocalPassword(7L)).thenReturn(false);
        when(socialAccountService.disconnectFromUser(7L, SocialProvider.GOOGLE))
                .thenReturn(SocialDisconnectionResult.DISCONNECTED);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/mypage/account/social-connections/google/disconnect")
                        .param("userId", "999")
                        .session(session)
                        .with(user(socialPrincipal(7L)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"));

        verify(socialAccountService).disconnectFromUser(7L, SocialProvider.GOOGLE);
        verify(socialAccountService, never()).disconnectFromUser(
                999L, SocialProvider.GOOGLE);
        assertThat(session.getAttribute(SocialConnectionNotice.SESSION_ATTRIBUTE))
                .isEqualTo(new SocialConnectionNotice(
                        SocialConnectionNotice.Type.DISCONNECTED, SocialProvider.GOOGLE));
    }

    @Test
    void lastLoginMethodRefusalIsShownWithoutDeletingAccountData() throws Exception {
        when(accountService.hasLocalPassword(77L)).thenReturn(false);
        when(userMapper.hasLocalPasswordById(77L)).thenReturn(false);
        when(socialAccountService.disconnectFromUser(77L, SocialProvider.NAVER))
                .thenReturn(SocialDisconnectionResult.LAST_LOGIN_METHOD);
        when(socialAccountService.findAllByUserId(77L)).thenReturn(List.of(
                socialAccount(77L, SocialProvider.NAVER, "naver-subject", null)));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/mypage/account/social-connections/naver/disconnect")
                        .session(session)
                        .with(user(socialPrincipal(77L)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"));

        mockMvc.perform(get("/mypage/account")
                        .session(session)
                        .with(user(socialPrincipal(77L))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "마지막 로그인 수단은 연결 해제할 수 없습니다. 다른 로그인 수단을 먼저 추가해주세요.")));

        verify(accountService, never()).withdrawAfterSocialReauthentication(77L);
    }

    @Test
    void unsupportedDisconnectProviderIsHandledWithoutCallingService() throws Exception {
        when(accountService.hasLocalPassword(7L)).thenReturn(false);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/mypage/account/social-connections/unsupported/disconnect")
                        .session(session)
                        .with(user(socialPrincipal(7L)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"));

        verify(socialAccountService, never()).disconnectFromUser(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(SocialProvider.class));
        assertThat(session.getAttribute(SocialConnectionNotice.SESSION_ATTRIBUTE))
                .isEqualTo(new SocialConnectionNotice(
                        SocialConnectionNotice.Type.DISCONNECT_ERROR, null));
    }

    @Test
    void disconnectFailureReturnsSafeNoticeWithoutExposingTheException() throws Exception {
        when(accountService.hasLocalPassword(7L)).thenReturn(false);
        when(socialAccountService.disconnectFromUser(7L, SocialProvider.KAKAO))
                .thenThrow(new IllegalStateException("database connection details"));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/mypage/account/social-connections/kakao/disconnect")
                        .session(session)
                        .with(user(socialPrincipal(7L)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("database connection details"))));

        assertThat(session.getAttribute(SocialConnectionNotice.SESSION_ATTRIBUTE))
                .isEqualTo(new SocialConnectionNotice(
                        SocialConnectionNotice.Type.DISCONNECT_ERROR, SocialProvider.KAKAO));
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
                        .param("confirmationPhrase", "탈퇴를 신청합니다"))
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
                        .param("currentPassword", "Password!")
                        .param("next", "password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account/edit"));

        assertThat(reauthenticationService.isVerified(session, 7L)).isTrue();
    }

    @Test
    void verificationReturnsOnlyToAnAllowedDestination() throws Exception {
        when(accountService.verifyCurrentPassword(7L, "Password!")).thenReturn(true);

        mockMvc.perform(post("/mypage/account/verify-password")
                        .with(user(principal(7L, UserRole.USER))).with(csrf())
                        .param("currentPassword", "Password!").param("next", "withdraw"))
                .andExpect(redirectedUrl("/mypage/account/withdraw"));
        // 소셜 연결을 바꾸려다 확인한 경우: 계정 관리로 돌아가 다시 누르도록 안내한다.
        mockMvc.perform(post("/mypage/account/verify-password")
                        .with(user(principal(7L, UserRole.USER))).with(csrf())
                        .param("currentPassword", "Password!").param("next", "account"))
                .andExpect(redirectedUrl("/mypage/account"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attributeExists("verifiedMessage"));
        // 목록에 없는 값은 외부 주소로 보내지 않고 계정 관리로 돌아간다.
        mockMvc.perform(post("/mypage/account/verify-password")
                        .with(user(principal(7L, UserRole.USER))).with(csrf())
                        .param("currentPassword", "Password!").param("next", "//evil.example"))
                .andExpect(redirectedUrl("/mypage/account"));
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

    /** 비밀번호 변경·탈퇴 화면은 주소로 바로 들어와도 비밀번호 확인을 건너뛸 수 없다. */
    @Test
    void directEditAccessWithoutRecentVerificationRedirects() throws Exception {
        mockMvc.perform(get("/mypage/account/edit")
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account/verify?next=password"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attributeExists("verificationMessage"));
        mockMvc.perform(get("/mypage/account/withdraw")
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(redirectedUrl("/mypage/account/verify?next=withdraw"));

        verify(accountService, never()).getAccountDetails(7L);
    }

    @Test
    void verifiedEditPageOnlyChangesThePassword() throws Exception {
        MockHttpSession memberSession = new MockHttpSession();
        reauthenticationService.markVerified(memberSession, 7L);
        mockMvc.perform(get("/mypage/account/edit")
                        .session(memberSession)
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/account-edit"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/mypage/account/password\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"newPassword\"")))
                // 소셜 연결·탈퇴는 이 화면에 중복해서 두지 않는다.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("social-connection-title"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/mypage/account/social-connections"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("action=\"/mypage/account/withdraw\""))));
    }

    @Test
    void userAndAdminCanOpenVerifiedWithdrawalPageUsingPrincipalId() throws Exception {
        MockHttpSession memberSession = new MockHttpSession();
        reauthenticationService.markVerified(memberSession, 7L);
        mockMvc.perform(get("/mypage/account/withdraw")
                        .session(memberSession)
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/account-withdraw"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/mypage/account/withdraw\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("탈퇴를 신청합니다")));

        MockHttpSession adminSession = new MockHttpSession();
        reauthenticationService.markVerified(adminSession, 99L);
        mockMvc.perform(get("/mypage/account/withdraw")
                        .session(adminSession)
                        .with(user(principal(99L, UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "관리자 계정은 마이페이지에서 탈퇴할 수 없습니다.")));
    }

    @Test
    void directDetailsUpdateCannotModifyReadonlyPersonalInformation() throws Exception {
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

        verify(accountService, never()).updateAccountDetails(eq(7L), any());
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
                .thenReturn(details("admin@example.com"));
        doThrow(new AccountValidationException(
                null, "관리자 계정은 마이페이지에서 탈퇴할 수 없습니다."))
                .when(accountService).withdraw(99L, "탈퇴를 신청합니다");

        mockMvc.perform(post("/mypage/account/withdraw")
                        .session(session)
                        .with(user(principal(99L, UserRole.ADMIN)))
                        .with(csrf())
                        .param("confirmationPhrase", "탈퇴를 신청합니다"))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/account-withdraw"));

        assertThat(session.isInvalid()).isFalse();
    }

    /**
     * 비밀번호 재입력을 없앤 대신 진입 단계의 재인증이 유일한 본인 확인이다.
     * 재인증 없이 탈퇴 POST 를 직접 호출하면 기존 requireVerification 정책이 그대로 막는다.
     */
    @Test
    void withdrawalWithoutARecentReauthenticationIsBlocked() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/mypage/account/withdraw")
                        .session(session)
                        .with(user(principal(7L, UserRole.USER)))
                        .with(csrf())
                        .param("confirmationPhrase", "탈퇴를 신청합니다"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account/verify?next=withdraw"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attributeExists("verificationMessage"));

        verify(accountService, never()).withdraw(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
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
                        .param("confirmationPhrase", "탈퇴를 신청합니다"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?withdrawn=true"));

        // 비밀번호를 다시 받지 않는다. 본인 확인은 진입 단계의 재인증이 이미 끝냈다.
        verify(accountService).withdraw(7L, "탈퇴를 신청합니다");
        verify(accountService, never()).verifyCurrentPassword(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void accountAndSecurityPageOmitsPersonalInformationAndKeepsRequiredActions()
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        reauthenticationService.markVerified(session, 7L);
        when(accountService.getAccountDetails(7L))
                .thenReturn(details("member@example.com"));

        mockMvc.perform(get("/mypage/account").session(session)
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s).*id=\"account-info-title\".*id=\"login-security-title\""
                                + ".*id=\"withdrawal-title\".*")))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s).*id=\"login-security-title\".*비밀번호 변경"
                                + ".*id=\"social-connection-title\".*")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("계정 및 보안")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("personal-info-title"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("여행 민준"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("010-1234-5678"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("2000-01-02"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("name=\"fullName\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("name=\"userPhone\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("name=\"userBirth\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("회원정보 저장"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("본인인증 완료"))));

        mockMvc.perform(post("/mypage/account/edit").session(session)
                        .with(user(principal(7L, UserRole.USER))).with(csrf())
                        .param("fullName", "여행 민준")
                        .param("userPhone", "010-1234-5678")
                        .param("userBirth", "1900-01-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/account/edit"));

        verify(accountService, never()).updateAccountDetails(eq(7L), any());
    }

    private AccountDetailsDto details(String email) {
        AccountDetailsDto details = new AccountDetailsDto();
        details.setUserEmail(email);
        details.setFullName("여행 민준");
        details.setUserPhone("010-1234-5678");
        details.setUserBirth(LocalDate.of(2000, 1, 2));
        return details;
    }

    private CustomUserDetails principal(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
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
