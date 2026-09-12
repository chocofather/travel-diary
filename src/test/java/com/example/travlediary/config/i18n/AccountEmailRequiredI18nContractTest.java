package com.example.travlediary.config.i18n;

import com.example.travlediary.controller.user.AccountEmailRequiredController;
import com.example.travlediary.model.PendingEmailCorrection;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.MissingEmailRegistrationService;
import jakarta.servlet.http.Cookie;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예전 소셜 회원 이메일 등록 화면의 5개 언어 계약.
 * 회원가입이 아니라 이메일 보완이므로 닉네임·약관 입력이 화면에 없어야 한다.
 */
@WebMvcTest(AccountEmailRequiredController.class)
@Import(I18nConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountEmailRequiredI18nContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MissingEmailRegistrationService missingEmailRegistrationService;
    @MockitoBean
    private com.example.travlediary.service.user.EmailCorrectionService
            emailCorrectionService;
    @MockitoBean
    private com.example.travlediary.repository.user.UserMapper userMapper;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 이메일        | 중복확인   | 인증메일 받기            | 로그아웃",
            "en    | Email        | Check     | Send verification email | Log out",
            "ja    | メールアドレス | 重複確認   | 認証メールを受け取る       | ログアウト",
            "zh-CN | 邮箱          | 重复确认   | 接收验证邮件             | 退出登录",
            "zh-TW | 電子郵件      | 重複確認   | 接收驗證信               | 登出"
    })
    void theScreenRendersInEverySupportedLanguage(String cookie, String emailLabel,
                                                  String check, String submit, String logout)
            throws Exception {
        Document page = render(cookie);

        assertThat(page.title()).isNotBlank().doesNotContain("??");
        assertThat(page.selectFirst("#emailRequiredTitle").text()).isNotBlank();
        assertThat(page.selectFirst("label[for=userEmail]").text()).isEqualTo(emailLabel);
        assertThat(page.selectFirst("#checkEmailStatus").text()).isEqualTo(check);
        assertThat(page.selectFirst(".login-submit").text()).isEqualTo(submit);
        assertThat(page.selectFirst("#emailRequiredLogout").text()).isEqualTo(logout);
        assertThat(page.selectFirst("#emailMessage").text()).isNotBlank();
        assertThat(page.selectFirst(".login-container").text()).doesNotContain("??", "{0}");

        for (String attribute : new String[]{"data-msg-available", "data-msg-unavailable",
                "data-msg-invalid", "data-msg-check-failed"}) {
            assertThat(page.selectFirst("#accountEmailField").attr(attribute))
                    .as("%s in %s", attribute, cookie).isNotBlank();
        }
    }

    /** 회원가입이 아니라 이메일 보완이다. 닉네임도 약관도 받지 않는다. */
    @Test
    void theScreenNeverAsksForANicknameOrConsentsAgain() throws Exception {
        Document page = render("ko");

        assertThat(page.selectFirst("#nickname")).isNull();
        assertThat(page.selectFirst("#generateNickname")).isNull();
        assertThat(page.selectFirst("input[name=nickname]")).isNull();
        assertThat(page.select("input[type=checkbox]")).isEmpty();
        assertThat(page.selectFirst(".social-signup__consents")).isNull();
        // 이메일과 CSRF 토큰 외의 입력은 두지 않는다.
        assertThat(page.select("form[action=/account/email-required] input"))
                .allMatch(input -> "userEmail".equals(input.attr("name"))
                        || "hidden".equals(input.attr("type")));
    }


    /** 이메일 변경 화면도 5개 언어로 나오고 닉네임·약관을 받지 않는다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 이메일 주소 변경        | 새 이메일",
            "en    | Change email address   | New email",
            "ja    | メールアドレスの変更     | 新しいメールアドレス",
            "zh-CN | 更改邮箱地址            | 新邮箱",
            "zh-TW | 變更電子郵件地址         | 新的電子郵件"
    })
    void theChangeScreenRendersInEverySupportedLanguage(String cookie, String title,
                                                        String newEmailLabel) throws Exception {
        Document page = renderChange(cookie);

        assertThat(page.selectFirst("#emailChangeTitle").text()).isEqualTo(title);
        assertThat(page.selectFirst("label[for=userEmail]").text()).isEqualTo(newEmailLabel);
        assertThat(page.selectFirst("#cancelEmailChange").text()).isNotBlank();
        assertThat(page.selectFirst(".login-container").text()).doesNotContain("??", "{0}");
        // 현재 이메일은 가려서만 보여준다.
        assertThat(page.selectFirst("#maskedCurrentEmail").text()).isEqualTo("ty***@example.com");
        // 회원가입 항목은 없다.
        assertThat(page.selectFirst("#nickname")).isNull();
        assertThat(page.select("input[type=checkbox]")).isEmpty();
    }

    /** 변경 화면의 message key 도 5개 번들에 모두 있다. */
    @Test
    void everyMessageKeyUsedByTheChangeScreenExistsInAllFiveBundles() throws Exception {
        assertKeysResolve("src/main/resources/templates/account/email-change.html");
    }


    /** 비밀번호 재확인 화면도 5개 언어로 나오고 아이디를 다시 입력받지 않는다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 본인 확인       | 비밀번호",
            "en    | Confirm it is you | Password",
            "ja    | 本人確認         | パスワード",
            "zh-CN | 身份确认         | 密码",
            "zh-TW | 身分確認         | 密碼"
    })
    void thePasswordCheckScreenRendersInEverySupportedLanguage(String cookie, String title,
                                                               String label) throws Exception {
        Document page = renderPasswordCheck(cookie);

        assertThat(page.selectFirst("#emailChangePasswordTitle").text()).isEqualTo(title);
        assertThat(page.selectFirst("label[for=currentPassword]").text()).isEqualTo(label);
        assertThat(page.selectFirst("#cancelPasswordCheck").text()).isNotBlank();
        assertThat(page.selectFirst(".login-container").text()).doesNotContain("??", "{0}");
        assertThat(page.selectFirst("#maskedCurrentEmail").text()).isEqualTo("ty***@example.com");
        // 아이디는 서버가 알고 있으므로 다시 받지 않는다. 대상 식별값도 화면에 없다.
        assertThat(page.selectFirst("input[name=username]")).isNull();
        assertThat(page.selectFirst("input[name=userId]")).isNull();
        assertThat(page.select("form[action=/account/email-required/change/password] input"))
                .allMatch(input -> "currentPassword".equals(input.attr("name"))
                        || "hidden".equals(input.attr("type")));
    }

    @Test
    void everyMessageKeyUsedByThePasswordScreenExistsInAllFiveBundles() throws Exception {
        assertKeysResolve("src/main/resources/templates/account/email-change-password.html");
    }

    private Document renderPasswordCheck(String cookie) throws Exception {
        Instant now = Instant.now();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE,
                new PendingEmailCorrection("flow", 23L, "typo@example.com",
                        PendingEmailCorrection.Method.LOCAL, null, false,
                        now.minusSeconds(10), now.plusSeconds(590)));
        return Jsoup.parse(mockMvc.perform(get("/account/email-required/change/password")
                        .session(session)
                        .cookie(new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, cookie)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Document renderChange(String cookie) throws Exception {
        Instant now = Instant.now();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE,
                new PendingEmailCorrection("flow", 23L, "typo@example.com",
                        PendingEmailCorrection.Method.SOCIAL, SocialProvider.KAKAO, true,
                        now.minusSeconds(10), now.plusSeconds(590)));
        return Jsoup.parse(mockMvc.perform(get("/account/email-required/change")
                        .session(session)
                        .cookie(new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, cookie)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** 템플릿이 쓰는 message key 가 5개 번들에 모두 존재한다. */
    @Test
    void everyMessageKeyUsedByTheScreenExistsInAllFiveBundles() throws Exception {
        assertKeysResolve("src/main/resources/templates/account/email-required.html");
    }

    private void assertKeysResolve(String templatePath) throws Exception {
        String template = Files.readString(Path.of(templatePath), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("#\\{([A-Za-z0-9._]+)").matcher(template);
        Set<String> keys = new LinkedHashSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        assertThat(keys).isNotEmpty();

        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messages.setFallbackToSystemLocale(false);
        for (SupportedLanguage language : SupportedLanguage.all()) {
            for (String key : keys) {
                assertThat(messages.getMessage(key, null, null, language.getLocale()))
                        .as("%s in %s bundle", key, language.getLanguageTag()).isNotNull();
            }
        }
    }

    /** 안내 문구는 전부 서버가 내려준다. JS 에 한국어 문자열이 없다. */
    @Test
    void theScreenScriptCarriesNoHardcodedKoreanText() throws Exception {
        String script = Files.readString(
                Path.of("src/main/resources/static/js/account-email-required.js"),
                StandardCharsets.UTF_8);

        assertThat(script).contains("data(\"msg-available\")", "data(\"msg-unavailable\")");
        assertThat(Pattern.compile("[\"'][^\"'\\n]*[가-힣][^\"'\\n]*[\"']").matcher(script).find())
                .as("hardcoded Korean string literal in account-email-required.js")
                .isFalse();
    }

    /** 보안 필터를 끈 슬라이스라 @AuthenticationPrincipal 이 읽을 컨텍스트를 직접 세운다. */
    private Document render(String cookie) throws Exception {
        when(missingEmailRegistrationService.requiresEmailRegistration(anyLong()))
                .thenReturn(true);
        CustomUserDetails principal = new CustomUserDetails(target());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            return Jsoup.parse(mockMvc.perform(get("/account/email-required")
                            .cookie(new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, cookie)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private User target() {
        User user = new User();
        user.setId(7L);
        user.setUsername("legacy");
        user.setNickname("예전회원");
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
