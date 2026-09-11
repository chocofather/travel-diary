package com.example.travlediary.config.i18n;

import com.example.travlediary.controller.user.WithdrawalPendingController;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AccountRecoveryService;
import com.example.travlediary.service.user.AccountRecoveryService.RecoveryRequestOutcome;
import jakarta.servlet.http.Cookie;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 탈퇴 유예 전용 안내 화면의 5개 언어 계약과 복구 요청 계약.
 * 이 화면이 유일한 복구 진입점이므로 이메일을 다시 묻지 않아야 한다.
 */
@WebMvcTest(WithdrawalPendingController.class)
@Import({I18nConfig.class, com.example.travlediary.config.SecurityConfig.class})
class WithdrawalPendingI18nContractTest {

    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 9, 11, 17, 20);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private AccountRecoveryService accountRecoveryService;
    @MockitoBean
    private com.example.travlediary.config.CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private com.example.travlediary.config.CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | ko    | 탈퇴 처리 중인 계정입니다.        | 탈퇴 신청일   | 복구 링크 받기      | 로그아웃",
            "en    | en    | This account is pending deletion.| Requested on| Send recovery link| Log out",
            "ja    | ja    | 退会手続き中のアカウントです。      | 退会申請日    | 復旧リンクを受け取る | ログアウト",
            "zh-CN | zh-CN | 该账号正在注销处理中。            | 注销申请日    | 获取恢复链接        | 退出登录",
            "zh-TW | zh-TW | 此帳號正在註銷處理中。            | 註銷申請日    | 取得復原連結        | 登出"
    })
    void theNoticeScreenRendersInEverySupportedLanguage(String cookie, String expectedLang,
                                                        String title, String requestedLabel,
                                                        String recovery, String logout)
            throws Exception {
        givenWithdrawalPendingAccount(LocalDateTime.now().plusDays(29).plusHours(23));

        Document page = render(get("/account/withdrawal-pending")
                .with(user(principal())).cookie(localeCookie(cookie)));

        assertThat(page.selectFirst("html").attr("lang")).isEqualTo(expectedLang);
        assertThat(page.title()).isNotBlank().doesNotContain("??");
        assertThat(page.selectFirst("#withdrawalPendingTitle").text()).isEqualTo(title);
        assertThat(page.select(".withdrawal-pending-detail dt").eachText())
                .contains(requestedLabel);
        assertThat(page.selectFirst(".login-submit").text()).isEqualTo(recovery);
        assertThat(page.selectFirst(".login-secondary").text()).isEqualTo(logout);
        assertThat(page.selectFirst(".recovery-note").text()).isNotBlank();
    }

    /** 신청일·최종 삭제 예정일·남은 기간이 모두 나온다. */
    @Test
    void theNoticeScreenShowsTheScheduleAndTheRemainingTime() throws Exception {
        givenWithdrawalPendingAccount(LocalDateTime.now().plusDays(29).plusHours(23));

        Document page = render(get("/account/withdrawal-pending").with(user(principal())));

        assertThat(page.select(".withdrawal-pending-detail > div")).hasSize(3);
        assertThat(page.selectFirst("#purgeScheduledAt").text()).isNotBlank().isNotEqualTo("-");
        assertThat(page.selectFirst("#withdrawalRemaining").text()).contains("29");
        // 초 단위로 흔들리는 카운트다운은 두지 않는다.
        assertThat(page.selectFirst("#withdrawalRemaining").text()).doesNotContain("초");
    }

    /** 유예기간이 이미 지나도 화면은 떠야 한다. 안 그러면 격리 필터와 루프가 생긴다. */
    @Test
    void anExpiredGracePeriodStillRendersTheNoticeWithoutTheRecoveryButton() throws Exception {
        givenWithdrawalPendingAccount(LocalDateTime.now().minusMinutes(1));

        Document page = render(get("/account/withdrawal-pending").with(user(principal())));

        assertThat(page.selectFirst("#withdrawalPendingTitle")).isNotNull();
        assertThat(page.select("#recoveryLinkForm")).isEmpty();
        assertThat(page.selectFirst("#withdrawalRemaining").text())
                .isEqualTo("복구 가능 기간이 지났습니다.");
        // 로그아웃 경로는 항상 남는다.
        assertThat(page.selectFirst(".login-secondary")).isNotNull();
    }

    @Test
    void aMemberThatIsNoLongerPendingLeavesTheNoticeScreen() throws Exception {
        when(userMapper.findWithdrawalPendingById(7L)).thenReturn(null);

        mockMvc.perform(get("/account/withdrawal-pending").with(user(principal())))
                .andExpect(redirectedUrl("/"));
    }

    /* ---------- 복구 링크 발송 ---------- */

    /** 이메일 입력창이 없다. 서버가 로그인된 회원의 가입 주소를 찾아 보낸다. */
    @Test
    void theRecoveryRequestNeverAsksForAnEmailAgain() throws Exception {
        givenWithdrawalPendingAccount(LocalDateTime.now().plusDays(10));

        Document page = render(get("/account/withdrawal-pending").with(user(principal())));

        assertThat(page.select("input[type=email], input[name=userEmail]")).isEmpty();
        assertThat(page.selectFirst("#recoveryLinkForm").attr("action"))
                .isEqualTo("/account/withdrawal-pending/recovery-link");
        assertThat(page.selectFirst("#recoveryLinkForm").attr("method"))
                .isEqualToIgnoringCase("post");
    }

    @ParameterizedTest
    @CsvSource({"SENT", "COOLDOWN", "NOT_ELIGIBLE"})
    void theRecoveryRequestUsesTheAuthenticatedMemberAndReportsTheOutcome(String outcome)
            throws Exception {
        when(accountRecoveryService.requestRecoveryFor(7L))
                .thenReturn(RecoveryRequestOutcome.valueOf(outcome));

        mockMvc.perform(post("/account/withdrawal-pending/recovery-link")
                        .with(user(principal())).with(csrf()))
                .andExpect(redirectedUrl("/account/withdrawal-pending"))
                .andExpect(flash().attribute("recoveryLinkOutcome", outcome));

        verify(accountRecoveryService).requestRecoveryFor(7L);
    }

    @Test
    void anUnexpectedRecoveryFailureStillEndsOnTheNoticeScreen() throws Exception {
        when(accountRecoveryService.requestRecoveryFor(7L))
                .thenThrow(new IllegalStateException("mail down"));

        mockMvc.perform(post("/account/withdrawal-pending/recovery-link")
                        .with(user(principal())).with(csrf()))
                .andExpect(redirectedUrl("/account/withdrawal-pending"))
                .andExpect(flash().attribute("recoveryLinkOutcome", "NOT_ELIGIBLE"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "SENT         | ko    | recoveryLinkSent        | 복구 안내를 보내드렸습니다",
            "SENT         | en    | recoveryLinkSent        | recovery instructions",
            "COOLDOWN     | ko    | recoveryLinkCooldown    | 잠시 후 다시",
            "COOLDOWN     | ja    | recoveryLinkCooldown    | しばらく",
            "NOT_ELIGIBLE | zh-CN | recoveryLinkUnavailable | 目前无法发送",
            "NOT_ELIGIBLE | zh-TW | recoveryLinkUnavailable | 目前無法寄送"
    })
    void theOutcomeNoticeIsLocalized(String outcome, String cookie, String elementId,
                                     String expected) throws Exception {
        givenWithdrawalPendingAccount(LocalDateTime.now().plusDays(10));

        Document page = Jsoup.parse(mockMvc.perform(get("/account/withdrawal-pending")
                        .with(user(principal()))
                        .cookie(localeCookie(cookie))
                        .flashAttr("recoveryLinkOutcome", outcome))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst("#" + elementId).text()).contains(expected);
    }

    /** 발송 안내에는 원문 주소가 아니라 가려진 주소만 나온다. */
    @Test
    void theSentNoticeShowsOnlyAMaskedEmail() throws Exception {
        givenWithdrawalPendingAccount(LocalDateTime.now().plusDays(10));

        Document page = Jsoup.parse(mockMvc.perform(get("/account/withdrawal-pending")
                        .with(user(principal()))
                        .flashAttr("recoveryLinkOutcome", "SENT"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst("#recoveryLinkSent").text())
                .contains("mem***@gmail.com")
                .doesNotContain("member@gmail.com");
    }

    /* ---------- 키 존재 ---------- */

    @ParameterizedTest
    @CsvSource({"ko", "en", "ja", "zh-CN", "zh-TW"})
    void noUnresolvedMessageOrPlaceholderSurvives(String cookie) throws Exception {
        givenWithdrawalPendingAccount(LocalDateTime.now().plusDays(10));

        String html = mockMvc.perform(get("/account/withdrawal-pending")
                        .with(user(principal())).cookie(localeCookie(cookie))
                        .flashAttr("recoveryLinkOutcome", "SENT"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).as("unresolved message in %s", cookie).doesNotContain("??");
        assertThat(Jsoup.parse(html).selectFirst(".login-container").text())
                .doesNotContain("{0}").doesNotContain("{1}");
    }

    @Test
    void everyMessageKeyUsedByTheNoticeScreenExistsInAllFiveBundles() throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("#\\{([A-Za-z0-9._]+)").matcher(
                read("src/main/resources/templates/account/withdrawal-pending.html"));
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        keys.add("mypage.account.withdrawal.submit");
        keys.add("mypage.account.withdrawal.confirm.label");
        keys.add("mypage.account.withdrawal.confirm.phrase");
        keys.add("mypage.account.withdrawal.confirm.placeholder");
        keys.add("mypage.account.error.withdrawal.confirm.mismatch");

        assertThat(keys).isNotEmpty();
        for (SupportedLanguage language : SupportedLanguage.all()) {
            for (String key : keys) {
                assertThat(bundle().getMessage(key, null, null, language.getLocale()))
                        .as("%s in %s bundle", key, language.getLanguageTag()).isNotNull();
            }
        }
    }

    /* ---------- helpers ---------- */

    private void givenWithdrawalPendingAccount(LocalDateTime purgeScheduledAt) {
        User account = new User();
        account.setId(7L);
        account.setUsername("travler");
        account.setUserEmail("member@gmail.com");
        account.setStatus(UserStatus.WITHDRAWAL_PENDING);
        account.setWithdrawalRequestedAt(REQUESTED_AT);
        account.setPurgeScheduledAt(purgeScheduledAt);
        when(userMapper.findWithdrawalPendingById(7L)).thenReturn(account);
    }

    private CustomUserDetails principal() {
        User user = new User();
        user.setId(7L);
        user.setUsername("travler");
        user.setUserPassword("encoded");
        user.setUserRole(UserRole.USER);
        return new CustomUserDetails(user);
    }

    private Document render(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Cookie localeCookie(String languageTag) {
        return new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, languageTag);
    }

    private ResourceBundleMessageSource bundle() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
