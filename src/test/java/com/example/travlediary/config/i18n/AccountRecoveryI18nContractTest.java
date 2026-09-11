package com.example.travlediary.config.i18n;

import com.example.travlediary.controller.user.AccountRecoveryController;
import com.example.travlediary.controller.user.LoginController;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.LoginThrottle;
import com.example.travlediary.security.LoginThrottleStatus;
import com.example.travlediary.service.user.AccountRecoveryService;
import jakarta.servlet.http.Cookie;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메일 복구 링크 화면의 5개 언어 계약과 응답 계약.
 * 복구 요청(발송)은 탈퇴 유예 안내 화면으로 옮겼으므로 여기에는 이메일 입력 화면이 없다.
 */
@WebMvcTest({AccountRecoveryController.class, LoginController.class})
@Import(I18nConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountRecoveryI18nContractTest {

    private static final String TOKEN = "recovery-token-123";
    private static final String INVALID_LINK_URL = "/login?recoveryLinkInvalid=true";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountRecoveryService accountRecoveryService;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private LoginThrottle loginThrottle;

    @BeforeEach
    void allowLoginByDefault() {
        when(loginThrottle.ipStatus("127.0.0.1")).thenReturn(new LoginThrottleStatus(0, null));
    }

    /* ---------- 링크 진입(GET) 은 상태를 바꾸지 않는다 ---------- */

    /**
     * 메일 보안 스캐너나 링크 미리보기가 대신 열어 볼 수 있는 요청이다.
     * GET 만으로는 복구도, 토큰 소비도 일어나지 않아야 한다.
     */
    @Test
    void openingTheLinkOnlyShowsAConfirmationAndChangesNothing() throws Exception {
        when(accountRecoveryService.isRecoveryLinkUsable(TOKEN)).thenReturn(true);

        Document page = render(get("/users/recover-account/confirm").param("token", TOKEN));

        assertThat(page.selectFirst("#recoverAccountConfirmForm").attr("method"))
                .isEqualToIgnoringCase("post");
        assertThat(page.selectFirst("#recoverAccountConfirmForm").attr("action"))
                .isEqualTo("/users/recover-account/confirm");
        assertThat(page.selectFirst("input[name=token]").val()).isEqualTo(TOKEN);
        verify(accountRecoveryService, never()).confirmRecovery(anyString());
    }

    @Test
    void repeatedAutomatedVisitsNeverTriggerTheRecoveryTransaction() throws Exception {
        when(accountRecoveryService.isRecoveryLinkUsable(TOKEN)).thenReturn(true);

        for (int visit = 0; visit < 5; visit++) {
            mockMvc.perform(get("/users/recover-account/confirm").param("token", TOKEN))
                    .andExpect(status().isOk());
        }

        verify(accountRecoveryService, never()).confirmRecovery(anyString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | ko    | 계정을 복구하시겠습니까?      | 계정 복구        | 취소",
            "en    | en    | Recover this account?      | Recover account| Cancel",
            "ja    | ja    | アカウントを復旧しますか？     | アカウント復旧    | キャンセル",
            "zh-CN | zh-CN | 要恢复该账号吗？             | 恢复账号         | 取消",
            "zh-TW | zh-TW | 要復原此帳號嗎？             | 復原帳號         | 取消"
    })
    void theConfirmationPageRendersInEverySupportedLanguage(String cookie, String expectedLang,
                                                            String title, String submit,
                                                            String cancel) throws Exception {
        when(accountRecoveryService.isRecoveryLinkUsable(TOKEN)).thenReturn(true);

        Document page = render(get("/users/recover-account/confirm")
                .param("token", TOKEN).cookie(localeCookie(cookie)));

        assertThat(page.selectFirst("html").attr("lang")).isEqualTo(expectedLang);
        assertThat(page.title()).isNotBlank().doesNotContain("??");
        assertThat(page.selectFirst("#recoveryConfirmTitle").text()).isEqualTo(title);
        assertThat(page.selectFirst(".login-header p").text()).isNotBlank();
        assertThat(page.selectFirst(".login-submit").text()).isEqualTo(submit);
        assertThat(page.select(".account-recovery a").eachText()).contains(cancel);
    }

    /* ---------- 만료/잘못된 링크 ---------- */

    @Test
    void anInvalidLinkRedirectsToLoginInsteadOfFailingHard() throws Exception {
        when(accountRecoveryService.isRecoveryLinkUsable(TOKEN)).thenReturn(false);

        mockMvc.perform(get("/users/recover-account/confirm").param("token", TOKEN))
                .andExpect(redirectedUrl(INVALID_LINK_URL));

        verify(accountRecoveryService, never()).confirmRecovery(anyString());
    }

    @Test
    void anUnexpectedLinkCheckFailureAlsoEndsOnTheSameNotice() throws Exception {
        when(accountRecoveryService.isRecoveryLinkUsable(TOKEN))
                .thenThrow(new IllegalStateException("lookup failed"));

        mockMvc.perform(get("/users/recover-account/confirm").param("token", TOKEN))
                .andExpect(redirectedUrl(INVALID_LINK_URL));
    }

    /** 확인 화면과 제출 사이에 만료되거나 유예기간이 끝난 경우도 같은 안내로 끝난다. */
    @Test
    void aSubmissionThatNoLongerQualifiesEndsOnTheSameNotice() throws Exception {
        when(accountRecoveryService.confirmRecovery(TOKEN)).thenReturn(false);

        mockMvc.perform(post("/users/recover-account/confirm").param("token", TOKEN))
                .andExpect(redirectedUrl(INVALID_LINK_URL));
    }

    @Test
    void anUnexpectedConfirmationFailureAlsoEndsOnTheSameNotice() throws Exception {
        when(accountRecoveryService.confirmRecovery(TOKEN))
                .thenThrow(new IllegalStateException("restore failed"));

        mockMvc.perform(post("/users/recover-account/confirm").param("token", TOKEN))
                .andExpect(redirectedUrl(INVALID_LINK_URL));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 복구 링크가 만료되었거나 이미 사용되었습니다.",
            "en    | This recovery link has expired or has already been used.",
            "ja    | 復旧リンクの有効期限が切れたか、すでに使用されています。",
            "zh-CN | 恢复链接已过期或已被使用。",
            "zh-TW | 復原連結已過期或已被使用。"
    })
    void theLoginScreenExplainsAnExpiredOrUsedLink(String cookie, String expected)
            throws Exception {
        Document page = render(get("/login")
                .param("recoveryLinkInvalid", "true")
                .cookie(localeCookie(cookie)));

        assertThat(page.selectFirst("#recoveryLinkInvalidFeedback .login-feedback__title").text())
                .isEqualTo(expected);
    }

    /* ---------- 복구 완료 ---------- */

    @Test
    void aSuccessfulRecoveryRedirectsToLoginWithoutSigningTheUserIn() throws Exception {
        when(accountRecoveryService.confirmRecovery(TOKEN)).thenReturn(true);

        mockMvc.perform(post("/users/recover-account/confirm").param("token", TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?accountRecovered=true"));
    }

    /** 탈퇴 유예 세션이 그대로 정상 세션으로 승격되지 않도록 세션을 정리한다. */
    @Test
    void aSuccessfulRecoveryClearsTheExistingSession() throws Exception {
        when(accountRecoveryService.confirmRecovery(TOKEN)).thenReturn(true);

        var session = new org.springframework.mock.web.MockHttpSession();
        mockMvc.perform(post("/users/recover-account/confirm")
                        .param("token", TOKEN).session(session))
                .andExpect(redirectedUrl("/login?accountRecovered=true"));

        assertThat(session.isInvalid()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 계정이 복구되었습니다. 다시 로그인해 주세요.",
            "en    | Your account has been restored. Please sign in again.",
            "ja    | アカウントを復旧しました。もう一度ログインしてください。",
            "zh-CN | 账号已恢复，请重新登录。",
            "zh-TW | 帳號已復原，請重新登入。"
    })
    void theLoginScreenExplainsTheCompletedRecovery(String cookie, String expected)
            throws Exception {
        Document page = render(get("/login")
                .param("accountRecovered", "true")
                .cookie(localeCookie(cookie)));

        assertThat(page.selectFirst("#accountRecoveredFeedback").text()).isEqualTo(expected);
    }

    /** 상태를 바꾸는 것은 POST 뿐이므로 CSRF 보호 대상에 들어가 있어야 한다. */
    @Test
    void theConfirmationPostIsProtectedByCsrf() throws Exception {
        String security = read("src/main/java/com/example/travlediary/config/SecurityConfig.java");

        assertThat(security)
                .contains("^/users/recover-account/confirm$")
                .doesNotContain("csrf(AbstractHttpConfigurer::disable)");
    }

    /* ---------- 복구 요청 UI 는 로그인 화면에서 사라졌다 ---------- */

    /**
     * 복구는 로그인 후 탈퇴 유예 전용 화면에서만 시작한다.
     * 이메일 입력형 공개 복구 요청 화면이 다시 살아나지 않도록 고정한다.
     */
    @Test
    void theLoginScreenNoLongerOffersAPublicRecoveryEntry() throws Exception {
        Document page = render(get("/login"));

        assertThat(page.select(".account-recovery-entry")).isEmpty();
        assertThat(page.select("a[href*=recover-account]")).isEmpty();
        assertThat(Path.of("src/main/resources/templates/recover-account.html")).doesNotExist();
    }

    @Test
    void thePublicRecoveryRequestEndpointIsGone() throws Exception {
        mockMvc.perform(get("/users/recover-account")).andExpect(status().isNotFound());
        mockMvc.perform(post("/users/recover-account").param("userEmail", "member@gmail.com"))
                .andExpect(status().isNotFound());
    }

    /* ---------- 잔여 하드코딩 / 키 존재 ---------- */

    @ParameterizedTest
    @CsvSource({"ko", "en", "ja", "zh-CN", "zh-TW"})
    void noUnresolvedMessageOrPlaceholderSurvives(String cookie) throws Exception {
        when(accountRecoveryService.isRecoveryLinkUsable(TOKEN)).thenReturn(true);

        String html = mockMvc.perform(get("/users/recover-account/confirm")
                        .param("token", TOKEN).cookie(localeCookie(cookie)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).as("unresolved message in %s", cookie).doesNotContain("??");
        assertThat(Jsoup.parse(html).selectFirst(".login-container").text())
                .doesNotContain("{0}").doesNotContain("{1}");
    }

    @Test
    void everyMessageKeyUsedByRecoveryScreensAndTheRecoveryMailExistsInAllFiveBundles()
            throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        for (String template : new String[]{
                "src/main/resources/templates/recover-account-confirm.html",
                "src/main/resources/templates/email/account-recovery-email.html"}) {
            Matcher matcher = Pattern.compile("#\\{([A-Za-z0-9._]+)").matcher(read(template));
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
        keys.add("mail.accountRecovery.subject");
        keys.add("login.status.accountRecovered");
        keys.add("account.recover.link.expired");
        keys.add("account.recover.link.retry");

        assertThat(keys).isNotEmpty();
        for (SupportedLanguage language : SupportedLanguage.all()) {
            for (String key : keys) {
                assertThat(bundle().getMessage(key, null, null, language.getLocale()))
                        .as("%s in %s bundle", key, language.getLanguageTag()).isNotNull();
            }
        }
    }

    /* ---------- helpers ---------- */

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
