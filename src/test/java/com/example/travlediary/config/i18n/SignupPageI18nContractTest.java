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
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 일반 회원가입 화면의 5개 언어 계약.
 * 고정 문구는 messages 번들에서만 오고, JS 문구는 data-* 로 전달된다.
 */
@WebMvcTest(UserController.class)
@Import(I18nConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class SignupPageI18nContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private com.example.travlediary.repository.user.UserMapper userMapper;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | ko    | 회원가입   | 아이디  | 비밀번호   | 닉네임    | 다음      | 필수",
            "en    | en    | Sign up   | Username| Password  | Nickname | Next     | Required",
            "ja    | ja    | 登録する   | ID      | パスワード | ニックネーム | 次へ   | 必須",
            "zh-CN | zh-CN | 注册      | 账号     | 密码      | 昵称      | 下一步   | 必选",
            "zh-TW | zh-TW | 註冊      | 帳號     | 密碼      | 暱稱      | 下一步   | 必填"
    })
    void signupFormRendersInEverySupportedLanguage(String cookie, String expectedLang,
                                                   String submit, String username, String password,
                                                   String nickname, String next, String required)
            throws Exception {
        Document page = render(get("/users/register").cookie(localeCookie(cookie)));

        assertThat(page.selectFirst("html").attr("lang")).isEqualTo(expectedLang);
        assertThat(page.title()).isNotBlank().doesNotContain("??");
        assertThat(page.selectFirst(".login-submit, .button-primary#step2-submit").text())
                .isEqualTo(submit);
        assertThat(page.selectFirst("label[for=username]").text()).isEqualTo(username);
        assertThat(page.selectFirst("label[for=userPassword]").text()).isEqualTo(password);
        assertThat(page.selectFirst("label[for=nickname]").text()).isEqualTo(nickname);
        assertThat(page.selectFirst("#step1-next").text()).isEqualTo(next);
        assertThat(page.selectFirst(".term-badge.required").text()).isEqualTo(required);
        // 단계 안내 / 도움말 / 약관 라벨도 번들에서 나온다.
        assertThat(page.selectFirst("#registrationStepStatus").text()).isNotBlank();
        assertThat(page.selectFirst("#usernameMessage").text()).isNotBlank();
        assertThat(page.selectFirst("#passwordValidationMessage").text()).isNotBlank();
        assertThat(page.selectFirst("#nicknameMessage").text()).isNotBlank();
        assertThat(page.selectFirst(".terms-summary strong").text()).isNotBlank();
    }

    /** 렌더링 결과 어디에도 미해석 message key 나 미치환 parameter 가 남으면 안 된다. */
    @ParameterizedTest
    @CsvSource({"ko", "en", "ja", "zh-CN", "zh-TW"})
    void noUnresolvedMessageOrPlaceholderSurvivesOnTheSignupPage(String cookie) throws Exception {
        String html = mockMvc.perform(get("/users/register").cookie(localeCookie(cookie)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).as("unresolved message in %s", cookie).doesNotContain("??");
        // 화면에 보이는 텍스트에 {0}/{1} 이 남지 않는다(JS 용 data-* 템플릿은 예외).
        assertThat(Jsoup.parse(html).selectFirst(".register-container").text())
                .doesNotContain("{0}").doesNotContain("{1}").doesNotContain("{2}");
    }

    /** Bean Validation 메시지도 현재 locale 의 번들에서 나온다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 아이디를 입력해주세요.        | 서비스 이용약관에 동의해주세요.",
            "en    | Please enter a username.     | Please accept the Terms of Service.",
            "ja    | IDを入力してください。         | サービス利用規約に同意してください。",
            "zh-CN | 请输入账号。                  | 请同意服务使用条款。",
            "zh-TW | 請輸入帳號。                  | 請同意服務使用條款。"
    })
    void validationMessagesAreLocalized(String cookie, String usernameRequired, String termsRequired)
            throws Exception {
        Document page = Jsoup.parse(mockMvc.perform(post("/users/register")
                        .cookie(localeCookie(cookie)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        List<String> errors = page.select("[data-field-error], .field-error").eachText();
        assertThat(errors).contains(usernameRequired, termsRequired);
        assertThat(page.select(".field-error").text()).doesNotContain("??").doesNotContain("{0}");
    }

    /** 로그인에서 만든 공용 언어 selector 를 그대로 쓴다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 한국어",
            "en    | English",
            "ja    | 日本語",
            "zh-CN | 简体中文",
            "zh-TW | 繁體中文"
    })
    void signupPageReusesTheSharedLanguageMenu(String cookie, String nativeName) throws Exception {
        Document page = render(get("/users/register").cookie(localeCookie(cookie)));

        var menu = page.selectFirst(".register-page__topbar .language-menu");
        assertThat(menu).isNotNull();
        assertThat(menu.selectFirst(".language-menu-current").text()).isEqualTo(nativeName);
        assertThat(menu.select(".language-menu-option > span:first-child").eachText())
                .containsExactly("한국어", "English", "日本語", "简体中文", "繁體中文");
        assertThat(menu.select("form.locale-option-form[action=/locale][method=post]")).hasSize(5);
        // 카드 안이 아니라 화면 상단에 독립적으로 놓이고, 전체 사이트 헤더는 들이지 않는다.
        assertThat(page.select(".register-container .language-menu")).isEmpty();
        assertThat(page.select("#site-menu, #auth-area")).isEmpty();
    }

    /** JS 문구는 하드코딩하지 않고 현재 locale 메시지를 data-* 로 받는다. */
    @Test
    void javascriptMessagesArePassedThroughDataAttributes() throws Exception {
        Document page = render(get("/users/register").cookie(localeCookie("en")));

        var form = page.selectFirst("#registrationForm");
        assertThat(form.attr("data-msg-username-taken")).isEqualTo("This username is already taken.");
        assertThat(form.attr("data-msg-checking")).isEqualTo("Checking availability...");
        assertThat(form.attr("data-msg-email-suggestion")).contains("{0}");
        assertThat(form.attr("data-msg-terms-view")).isEqualTo("View");
        assertThat(form.attr("data-msg-terms-hide")).isEqualTo("Hide");
        assertThat(page.selectFirst(".registration-progress").attr("data-step-status-format"))
                .contains("{0}").contains("{1}").contains("{2}");
        assertThat(page.selectFirst("#step2-submit").attr("data-submitting-label"))
                .isEqualTo("Creating your account...");
        assertThat(page.selectFirst("button[data-toggle='#userPassword']").attr("data-hide-label"))
                .isEqualTo("Hide password");
    }

    /** 템플릿이 쓰는 message key 가 5개 번들에 모두 존재하는지 직접 확인한다. */
    @Test
    void everyMessageKeyUsedBySignupTemplatesExistsInAllFiveBundles() throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        for (String template : new String[]{
                "src/main/resources/templates/register.html",
                "src/main/resources/templates/verify-waiting.html",
                "src/main/resources/templates/verification-resend.html",
                "src/main/resources/templates/verification-result.html"}) {
            Matcher matcher = Pattern.compile("#\\{([A-Za-z0-9._]+)")
                    .matcher(Files.readString(Path.of(template), StandardCharsets.UTF_8));
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
        assertThat(keys).isNotEmpty();
        // locale suffix 를 직접 붙인 key 가 섞이지 않았는지도 본다.
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
                        .as("%s in %s bundle", key, language.getLanguageTag())
                        .isNotNull();
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
