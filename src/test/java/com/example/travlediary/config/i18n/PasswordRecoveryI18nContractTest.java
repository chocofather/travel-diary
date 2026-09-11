package com.example.travlediary.config.i18n;

import com.example.travlediary.controller.user.UserController;
import com.example.travlediary.model.User;
import com.example.travlediary.service.user.PasswordPolicy;
import com.example.travlediary.service.user.UserService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비밀번호 찾기 / 재설정 화면의 5개 언어 계약.
 * 비밀번호 정책 문구는 회원가입 key 를 재사용하고, reset 화면은 ?token= 을 잃지 않아야 한다.
 */
@WebMvcTest(UserController.class)
@Import(I18nConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class PasswordRecoveryI18nContractTest {

    private static final String TOKEN = "reset-token-123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private com.example.travlediary.repository.user.UserMapper userMapper;

    /* ---------- 비밀번호 찾기 ---------- */

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | ko    | 비밀번호 재설정        | 아이디   | 가입 이메일           | 재설정 링크 보내기 | 로그인으로 돌아가기",
            "en    | en    | Reset your password  | Username| Registered email     | Send reset link  | Back to login",
            "ja    | ja    | パスワードを再設定     | ID      | 登録メールアドレス     | 再設定リンクを送信 | ログインへ戻る",
            "zh-CN | zh-CN | 重置密码              | 账号     | 注册邮箱             | 发送重置链接      | 返回登录",
            "zh-TW | zh-TW | 重設密碼              | 帳號     | 註冊電子郵件          | 寄送重設連結      | 返回登入"
    })
    void findPasswordRendersInEverySupportedLanguage(String cookie, String expectedLang,
                                                     String title, String username, String email,
                                                     String submit, String backToLogin)
            throws Exception {
        Document page = render(get("/users/find-password").cookie(localeCookie(cookie)));

        assertThat(page.selectFirst("html").attr("lang")).isEqualTo(expectedLang);
        assertThat(page.title()).isNotBlank().doesNotContain("??");
        assertThat(page.selectFirst("#recoveryTitle").text()).isEqualTo(title);
        assertThat(page.selectFirst("label[for=findPasswordUsername]").text()).isEqualTo(username);
        assertThat(page.selectFirst("label[for=findPasswordEmail]").text()).isEqualTo(email);
        assertThat(page.selectFirst(".login-submit").text()).isEqualTo(submit);
        assertThat(page.select(".account-recovery a").eachText()).contains(backToLogin);
        assertThat(page.selectFirst(".login-header p").text()).isNotBlank();
        assertThat(page.selectFirst("#findPasswordEmail").attr("placeholder")).isNotBlank();
    }

    /** 계정 존재 여부를 숨기는 완료 안내도 현재 언어로 나온다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 재설정 링크를 요청했어요",
            "en    | Reset link requested",
            "ja    | 再設定リンクを送信しました",
            "zh-CN | 已发送重置链接",
            "zh-TW | 已寄出重設連結"
    })
    void requestedNoticeIsLocalizedWithoutRevealingAccountExistence(String cookie, String title)
            throws Exception {
        Document page = Jsoup.parse(mockMvc.perform(get("/users/find-password")
                        .cookie(localeCookie(cookie))
                        .flashAttr("recoveryRequested", true))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst(".recovery-complete #recoveryTitle").text()).isEqualTo(title);
        assertThat(page.selectFirst(".recovery-note").text()).isNotBlank();
        assertThat(page.select(".login-form")).isEmpty();
    }

    /* ---------- 비밀번호 재설정 ---------- */

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | ko    | 새 비밀번호 설정         | 새 비밀번호        | 새 비밀번호 확인      | 비밀번호 변경",
            "en    | en    | Set a new password     | New password      | Confirm new password| Change password",
            "ja    | ja    | 新しいパスワードの設定    | 新しいパスワード     | 新しいパスワード（確認） | パスワードを変更",
            "zh-CN | zh-CN | 设置新密码              | 新密码            | 确认新密码           | 修改密码",
            "zh-TW | zh-TW | 設定新密碼              | 新密碼            | 確認新密碼           | 變更密碼"
    })
    void resetPasswordRendersInEverySupportedLanguage(String cookie, String expectedLang,
                                                      String title, String password,
                                                      String confirm, String submit)
            throws Exception {
        when(userService.validateResetToken(TOKEN)).thenReturn(new User());

        Document page = render(get("/users/reset-password")
                .param("token", TOKEN).cookie(localeCookie(cookie)));

        assertThat(page.selectFirst("html").attr("lang")).isEqualTo(expectedLang);
        assertThat(page.title()).isNotBlank().doesNotContain("??");
        assertThat(page.selectFirst("#resetPasswordTitle").text()).isEqualTo(title);
        assertThat(page.selectFirst("label[for=newPassword]").text()).isEqualTo(password);
        assertThat(page.selectFirst("label[for=newPasswordConfirm]").text()).isEqualTo(confirm);
        assertThat(page.selectFirst(".login-submit").text()).isEqualTo(submit);
        assertThat(page.selectFirst("#passwordPolicyHelp").text()).isNotBlank();
        assertThat(page.selectFirst("#passwordConfirmationHelp").text()).isNotBlank();
        // token 은 hidden 으로 그대로 유지된다.
        assertThat(page.selectFirst("input[name=token]").val()).isEqualTo(TOKEN);
    }

    /** 비밀번호 정책 문구는 회원가입과 같은 key(=PasswordPolicy 규칙)를 그대로 쓴다. */
    @ParameterizedTest
    @CsvSource({"ko", "en", "ja", "zh-CN", "zh-TW"})
    void passwordPolicyTextIsReusedFromTheSignupKey(String cookie) throws Exception {
        when(userService.validateResetToken(TOKEN)).thenReturn(new User());

        String rendered = render(get("/users/reset-password")
                .param("token", TOKEN).cookie(localeCookie(cookie)))
                .selectFirst("#passwordPolicyHelp").text();

        assertThat(rendered).isEqualTo(bundleMessage("signup.error.password.policy", cookie));
        // 규칙 자체는 그대로다.
        assertThat(PasswordPolicy.isValid("abcdefg1!")).isTrue();
        assertThat(PasswordPolicy.isValid("abcdefgh")).isFalse();
    }

    /** 서버 오류 문구도 현재 locale 로 나온다(검증 규칙은 그대로). */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | POLICY   | 비밀번호는 8자 이상이며, 영문, 숫자, !@#$%^&*만 사용하고 특수문자를 1개 이상 포함해야 합니다.",
            "en    | POLICY   | Passwords must be at least 8 characters using letters, numbers and !@#$%^&*, including at least one special character.",
            "ko    | MISMATCH | 새 비밀번호가 일치하지 않습니다.",
            "ja    | MISMATCH | 新しいパスワードが一致しません。",
            "zh-CN | SAME     | 请输入与当前密码不同的密码。",
            "zh-TW | SAME     | 請輸入與目前密碼不同的密碼。"
    })
    void resetErrorsAreLocalized(String cookie, String kind, String expected) throws Exception {
        String thrown = switch (kind) {
            case "POLICY" -> PasswordPolicy.INVALID_MESSAGE;
            case "MISMATCH" -> PasswordPolicy.MISMATCH_MESSAGE;
            default -> UserService.SAME_AS_CURRENT_PASSWORD_MESSAGE;
        };
        doThrow(new IllegalArgumentException(thrown))
                .when(userService).resetPassword(anyString(), anyString(), anyString());

        mockMvc.perform(post("/users/reset-password")
                        .cookie(localeCookie(cookie))
                        .param("token", TOKEN)
                        .param("newPassword", "whatever")
                        .param("newPasswordConfirm", "other"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", expected));
    }

    /** 성공하면 기존 흐름 그대로 로그인 화면의 완료 안내로 넘어간다. */
    @Test
    void successfulResetKeepsTheExistingPasswordChangedRedirect() throws Exception {
        mockMvc.perform(post("/users/reset-password")
                        .param("token", TOKEN)
                        .param("newPassword", "abcdefg1!")
                        .param("newPasswordConfirm", "abcdefg1!"))
                .andExpect(redirectedUrl("/login?passwordChanged=true"));
    }

    /* ---------- 언어 selector / token 보존 ---------- */

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 한국어",
            "en    | English",
            "ja    | 日本語",
            "zh-CN | 简体中文",
            "zh-TW | 繁體中文"
    })
    void resetScreenReusesTheSharedLanguageMenuAndKeepsTheToken(String cookie, String nativeName)
            throws Exception {
        when(userService.validateResetToken(TOKEN)).thenReturn(new User());

        Document page = Jsoup.parse(mockMvc.perform(
                        get(URI.create("/users/reset-password?token=" + TOKEN))
                                .cookie(localeCookie(cookie)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        var menu = page.selectFirst(".login-page__topbar .language-menu");
        assertThat(menu).isNotNull();
        assertThat(menu.selectFirst(".language-menu-current").text()).isEqualTo(nativeName);
        assertThat(menu.select("form.locale-option-form[action=/locale][method=post]")).hasSize(5);
        // 언어를 바꿔도 같은 화면으로 token 을 달고 돌아온다.
        assertThat(menu.select("form.locale-option-form input[name=returnTo]").eachAttr("value"))
                .hasSize(5)
                .allSatisfy(returnTo -> assertThat(returnTo)
                        .isEqualTo("/users/reset-password?token=" + TOKEN));
    }

    @Test
    void findPasswordScreenAlsoReusesTheSharedLanguageMenu() throws Exception {
        Document page = render(get("/users/find-password").cookie(localeCookie("ja")));

        assertThat(page.selectFirst(".login-page__topbar .language-menu")).isNotNull();
        assertThat(page.select(".login-container .language-menu")).isEmpty();
        assertThat(page.select("#site-menu, #auth-area")).isEmpty();
    }

    /* ---------- 잔여 하드코딩 / 키 존재 ---------- */

    @ParameterizedTest
    @CsvSource({"ko", "en", "ja", "zh-CN", "zh-TW"})
    void noUnresolvedMessageOrPlaceholderSurvives(String cookie) throws Exception {
        when(userService.validateResetToken(TOKEN)).thenReturn(new User());

        for (var request : java.util.List.of(
                get("/users/find-password").cookie(localeCookie(cookie)),
                get("/users/reset-password").param("token", TOKEN).cookie(localeCookie(cookie)))) {
            String html = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).as("unresolved message in %s", cookie).doesNotContain("??");
            assertThat(Jsoup.parse(html).selectFirst(".login-container").text())
                    .doesNotContain("{0}").doesNotContain("{1}");
        }
    }

    /** 일치 안내는 JS 하드코딩 대신 폼의 data-* 로 전달된다. */
    @Test
    void passwordMismatchMessageIsPassedThroughADataAttribute() throws Exception {
        when(userService.validateResetToken(TOKEN)).thenReturn(new User());

        Document page = render(get("/users/reset-password")
                .param("token", TOKEN).cookie(localeCookie("en")));

        assertThat(page.selectFirst("#resetPasswordForm").attr("data-password-mismatch"))
                .isEqualTo(bundleMessage("mypage.account.error.password.mismatch", "en"));
        assertThat(page.selectFirst("button[data-toggle='#newPassword']").attr("data-hide-label"))
                .isEqualTo(bundleMessage("login.password.hide", "en"));
        assertThat(read("src/main/resources/static/js/login.js"))
                .contains("dataset.passwordMismatch");
    }

    @Test
    void everyMessageKeyUsedByRecoveryTemplatesExistsInAllFiveBundles() throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        for (String template : new String[]{
                "src/main/resources/templates/find-password.html",
                "src/main/resources/templates/reset-password.html"}) {
            Matcher matcher = Pattern.compile("#\\{([A-Za-z0-9._]+)").matcher(read(template));
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
        assertThat(keys).isNotEmpty();
        assertThat(keys).noneMatch(key ->
                key.endsWith("_ko") || key.endsWith("_en") || key.endsWith("_ja")
                        || key.endsWith("_zh_CN") || key.endsWith("_zh_TW"));

        for (SupportedLanguage language : SupportedLanguage.all()) {
            for (String key : keys) {
                assertThat(bundle().getMessage(key, null, null, language.getLocale()))
                        .as("%s in %s bundle", key, language.getLanguageTag()).isNotNull();
            }
        }
    }

    /* ---------- helpers ---------- */

    private Document render(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return Jsoup.parse(mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Cookie localeCookie(String languageTag) {
        return new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, languageTag);
    }

    private String bundleMessage(String key, String languageTag) {
        return bundle().getMessage(key, null, java.util.Locale.forLanguageTag(languageTag));
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
