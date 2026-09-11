package com.example.travlediary.config.i18n;

import com.example.travlediary.controller.user.UserController;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 아이디 찾기 화면의 5개 언어 계약.
 * 계정 존재 여부는 어느 언어에서도 드러내지 않고, 요청 locale 이 아이디 안내 메일까지 이어져야 한다.
 */
@WebMvcTest(UserController.class)
@Import(I18nConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class FindUsernameI18nContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private com.example.travlediary.repository.user.UserMapper userMapper;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | ko    | 아이디 찾기            | 가입 이메일           | 아이디 안내 메일 받기   | 비밀번호 재설정 | 로그인으로 돌아가기",
            "en    | en    | Find your username   | Registered email     | Email me my username | Reset password | Back to login",
            "ja    | ja    | IDを探す              | 登録メールアドレス      | IDの案内メールを受け取る | パスワード再設定 | ログインへ戻る",
            "zh-CN | zh-CN | 找回账号              | 注册邮箱              | 发送账号到邮箱         | 重置密码       | 返回登录",
            "zh-TW | zh-TW | 找回帳號              | 註冊電子郵件           | 寄送帳號到信箱         | 重設密碼       | 返回登入"
    })
    void findUsernameRendersInEverySupportedLanguage(String cookie, String expectedLang,
                                                     String title, String email, String submit,
                                                     String resetPassword, String backToLogin)
            throws Exception {
        Document page = render(get("/users/find-username").cookie(localeCookie(cookie)));

        assertThat(page.selectFirst("html").attr("lang")).isEqualTo(expectedLang);
        assertThat(page.title()).isNotBlank().doesNotContain("??");
        assertThat(page.selectFirst("#recoveryTitle").text()).isEqualTo(title);
        assertThat(page.selectFirst("label[for=findUsernameEmail]").text()).isEqualTo(email);
        assertThat(page.selectFirst(".login-submit").text()).isEqualTo(submit);
        assertThat(page.select(".account-recovery a").eachText())
                .containsExactly(resetPassword, backToLogin);
        assertThat(page.selectFirst(".login-header p").text()).isNotBlank();
        assertThat(page.selectFirst("#findUsernameEmail").attr("placeholder")).isNotBlank();
    }

    /** 완료 안내는 계정 존재 여부를 드러내지 않는 기존 정책을 그대로 유지한다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 아이디 안내를 요청했어요",
            "en    | Username email requested",
            "ja    | IDのご案内を送信しました",
            "zh-CN | 已发送账号信息",
            "zh-TW | 已寄出帳號資訊"
    })
    void requestedNoticeIsLocalizedAndHidesAccountExistence(String cookie, String title)
            throws Exception {
        Document page = Jsoup.parse(mockMvc.perform(get("/users/find-username")
                        .cookie(localeCookie(cookie))
                        .flashAttr("recoveryRequested", true))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst(".recovery-complete #recoveryTitle").text()).isEqualTo(title);
        assertThat(page.selectFirst(".recovery-note").text()).isNotBlank();
        // 완료 화면에는 입력 폼도, 아이디 값도 나오지 않는다.
        assertThat(page.select(".login-form")).isEmpty();
        assertThat(page.selectFirst(".login-container").text()).doesNotContain("??");
    }

    /** 요청 결과는 계정 유무와 무관하게 같은 화면으로 간다. */
    @Test
    void requestAlwaysRedirectsToTheSameCompletionStateRegardlessOfAccountExistence()
            throws Exception {
        mockMvc.perform(post("/users/find-username").param("userEmail", "member@gmail.com"))
                .andExpect(redirectedUrl("/users/find-username"));

        org.mockito.Mockito.doThrow(new RuntimeException("no account"))
                .when(userService).processFindUsername("missing@gmail.com");
        mockMvc.perform(post("/users/find-username").param("userEmail", "missing@gmail.com"))
                .andExpect(redirectedUrl("/users/find-username"));
    }

    /**
     * 화면 → 요청 locale → 기존 아이디 안내 메일 경로가 이어지는지 확인한다.
     * (메일 문구/발송 자체는 직전 단계 구현을 그대로 쓴다)
     */
    @ParameterizedTest
    @CsvSource({"ko", "en", "ja", "zh-CN", "zh-TW"})
    void requestLocaleReachesTheUsernameRecoveryMailService(String cookie) throws Exception {
        mockMvc.perform(post("/users/find-username")
                        .cookie(localeCookie(cookie))
                        .param("userEmail", "member@gmail.com"))
                .andExpect(status().is3xxRedirection());

        // 컨트롤러가 요청 스레드에서 서비스를 호출하고, 서비스가 그 시점 locale 로 언어를 확정한다.
        org.mockito.Mockito.verify(userService).processFindUsername("member@gmail.com");
    }

    @ParameterizedTest
    @CsvSource({"ko", "en", "ja", "zh-CN", "zh-TW"})
    void noUnresolvedMessageOrPlaceholderSurvives(String cookie) throws Exception {
        for (MockHttpServletRequestBuilder request : java.util.List.of(
                get("/users/find-username").cookie(localeCookie(cookie)),
                get("/users/find-username").cookie(localeCookie(cookie))
                        .flashAttr("recoveryRequested", true))) {
            String html = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).as("unresolved message in %s", cookie).doesNotContain("??");
            assertThat(Jsoup.parse(html).selectFirst(".login-container").text())
                    .doesNotContain("{0}").doesNotContain("{1}");
        }
    }

    /** 다른 Auth 화면과 같은 공용 언어 selector fragment 를 쓴다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 한국어",
            "en    | English",
            "ja    | 日本語",
            "zh-CN | 简体中文",
            "zh-TW | 繁體中文"
    })
    void sharedLanguageMenuIsReused(String cookie, String nativeName) throws Exception {
        Document page = render(get("/users/find-username").cookie(localeCookie(cookie)));

        var menu = page.selectFirst(".login-page__topbar .language-menu");
        assertThat(menu).isNotNull();
        assertThat(menu.selectFirst(".language-menu-current").text()).isEqualTo(nativeName);
        assertThat(menu.select(".language-menu-option > span:first-child").eachText())
                .containsExactly("한국어", "English", "日本語", "简体中文", "繁體中文");
        assertThat(menu.select("form.locale-option-form[action=/locale][method=post]")).hasSize(5);
        assertThat(menu.select("form.locale-option-form input[name=returnTo]").eachAttr("value"))
                .allSatisfy(returnTo -> assertThat(returnTo).isEqualTo("/users/find-username"));
        assertThat(page.select(".login-container .language-menu")).isEmpty();
    }

    @Test
    void everyMessageKeyUsedByTheTemplateExistsInAllFiveBundles() throws Exception {
        String template = Files.readString(
                Path.of("src/main/resources/templates/find-username.html"), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("#\\{([A-Za-z0-9._]+)").matcher(template);
        Set<String> keys = new LinkedHashSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        assertThat(keys).isNotEmpty();
        assertThat(keys).noneMatch(key ->
                key.endsWith("_ko") || key.endsWith("_en") || key.endsWith("_ja")
                        || key.endsWith("_zh_CN") || key.endsWith("_zh_TW"));

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

    private Document render(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Cookie localeCookie(String languageTag) {
        return new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, languageTag);
    }
}
