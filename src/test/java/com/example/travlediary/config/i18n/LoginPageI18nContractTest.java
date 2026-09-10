package com.example.travlediary.config.i18n;

import com.example.travlediary.controller.user.LoginController;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.LoginFormState;
import com.example.travlediary.security.LoginThrottle;
import com.example.travlediary.security.LoginThrottleStatus;
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
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 화면 고정 문구의 5개 언어 계약.
 * 문구는 messages 번들에서만 오고, 동적인 숫자/시간은 message parameter 로 조립된다.
 */
@WebMvcTest(LoginController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoginPageI18nContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private LoginThrottle loginThrottle;

    /** 저장된 실패 이력이 없는 요청은 IP 기준 상태만 본다. */
    @BeforeEach
    void allowLoginByDefault() {
        when(loginThrottle.ipStatus("127.0.0.1"))
                .thenReturn(new LoginThrottleStatus(0, null));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | ko    | 로그인            | 아이디            | 비밀번호      | 또는 | 회원가입",
            "en    | en    | Log in           | Username         | Password     | or   | Sign up",
            "ja    | ja    | ログイン          | ID               | パスワード    | または| 会員登録",
            "zh-CN | zh-CN | 登录             | 账号              | 密码         | 或   | 注册",
            "zh-TW | zh-TW | 登入             | 帳號              | 密碼         | 或   | 註冊"
    })
    void loginFormRendersInEverySupportedLanguage(String cookie, String expectedLang,
                                                  String submit, String username,
                                                  String password, String divider,
                                                  String signUp) throws Exception {
        Document page = render(get("/login").cookie(localeCookie(cookie)));

        assertThat(page.selectFirst("html").attr("lang")).isEqualTo(expectedLang);
        assertThat(page.selectFirst("#loginTitle").text()).isEqualTo(submit);
        assertThat(page.selectFirst(".login-submit").text()).isEqualTo(submit);
        assertThat(page.selectFirst("label[for=username]").text()).isEqualTo(username);
        assertThat(page.selectFirst("label[for=loginPassword]").text()).isEqualTo(password);
        assertThat(page.selectFirst(".social-login__divider span").text()).isEqualTo(divider);
        assertThat(page.selectFirst(".signup-entry a").text()).isEqualTo(signUp);
        // placeholder / subtitle / 계정 찾기 문구도 번들에서 나와야 한다.
        assertThat(page.selectFirst("#username").attr("placeholder")).isNotBlank();
        assertThat(page.selectFirst("#loginPassword").attr("placeholder")).isNotBlank();
        assertThat(page.selectFirst(".login-header p").text()).isNotBlank();
        assertThat(page.selectFirst(".account-recovery").text()).isNotBlank();
        assertThat(page.selectFirst(".verification-resend-entry").text()).isNotBlank();
        // <title> 과 <html lang> 도 각각 message / language tag 로 채워진다.
        assertThat(page.title()).isNotBlank().doesNotContain("??");
        assertThat(page.selectFirst(".login-container").text()).doesNotContain("{0}");
    }

    /**
     * 렌더링 결과 어디에도 Thymeleaf 의 미해석 표시(??key_locale??)가 남으면 안 된다.
     * 본문 텍스트뿐 아니라 title / placeholder / aria-label / data-* 속성까지 원본 HTML 로 본다.
     */
    @ParameterizedTest
    @CsvSource({"ko", "en", "ja", "zh-CN", "zh-TW"})
    void noUnresolvedMessageMarkerSurvivesAnywhereOnThePage(String cookie) throws Exception {
        Instant blockedUntil = Instant.now().plusSeconds(70);
        when(loginThrottle.status("member", "127.0.0.1"))
                .thenReturn(new LoginThrottleStatus(5, blockedUntil));

        // 잠금 / 실패 / 로그아웃 / 비밀번호 변경 안내까지 한 번에 켜서 모든 분기를 렌더링한다.
        for (MockHttpServletRequestBuilder request : java.util.List.of(
                get("/login").cookie(localeCookie(cookie)),
                get("/login").cookie(localeCookie(cookie))
                        .param("error", "true").param("logout", "true")
                        .param("passwordChanged", "true").param("oauthError", "true")
                        .param("socialSignupExpired", "true").param("socialSignupError", "true")
                        .session(sessionWith(new LoginFormState("member", 4, null))),
                get("/login").cookie(localeCookie(cookie))
                        .session(sessionWith(new LoginFormState("member", 5, blockedUntil))))) {
            String html = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).as("unresolved message in %s render", cookie).doesNotContain("??");
        }
    }

    /**
     * 템플릿이 쓰는 message key 가 5개 번들에 모두 존재하는지 직접 확인한다.
     * key 오타나 한쪽 번들 누락이 생기면 렌더링 전에 여기서 잡힌다.
     */
    @Test
    void everyMessageKeyUsedByTheLoginTemplateExistsInAllFiveBundles() throws Exception {
        String template = Files.readString(
                Path.of("src/main/resources/templates/login.html"), StandardCharsets.UTF_8);
        Matcher keyMatcher = Pattern.compile("#\\{([A-Za-z0-9._]+)").matcher(template);
        Set<String> keys = new LinkedHashSet<>();
        while (keyMatcher.find()) {
            keys.add(keyMatcher.group(1));
        }
        assertThat(keys).as("login.html 이 쓰는 message key").isNotEmpty();
        // locale suffix 를 직접 붙인 key 가 섞이지 않았는지도 함께 본다.
        assertThat(keys).noneMatch(key ->
                key.endsWith("_ko") || key.endsWith("_en") || key.endsWith("_ja")
                        || key.endsWith("_zh_CN") || key.endsWith("_zh_TW"));

        // application.yml 과 같은 basename/encoding 으로 실제 번들을 읽는다.
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

    /** 브랜드명은 어느 언어에서도 번역하지 않는다. */
    @ParameterizedTest
    @CsvSource({"ko", "en", "ja", "zh-CN", "zh-TW"})
    void socialProviderBrandNamesAreNeverTranslated(String cookie) throws Exception {
        Document page = render(get("/login").cookie(localeCookie(cookie)));

        assertThat(page.select(".social-login-provider__label").eachText())
                .containsExactly("Google",
                        "ko".equals(cookie) ? "카카오" : "Kakao",
                        "ko".equals(cookie) ? "네이버" : "Naver");
        assertThat(page.selectFirst(".social-login-provider--google").attr("href"))
                .isEqualTo("/oauth2/authorization/google");
        assertThat(page.selectFirst(".social-login-provider--kakao").attr("href"))
                .isEqualTo("/oauth2/authorization/kakao");
        assertThat(page.selectFirst(".social-login-provider--naver").attr("href"))
                .isEqualTo("/oauth2/authorization/naver");
        assertThat(page.selectFirst(".social-login-provider--google").attr("aria-label"))
                .contains("Google");
    }

    /** 실패 횟수 안내는 언어별 어순대로 parameter 가 채워진다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 로그인 실패 3회 · 2회 더 실패하면 잠시 제한됩니다.",
            "en    | 3 failed attempts · 2 more will lock login for a moment.",
            "zh-TW | 登入失敗 3 次 · 再失敗 2 次將暫時限制登入。"
    })
    void failureCountMessageIsBuiltFromMessageParameters(String cookie, String expected)
            throws Exception {
        when(loginThrottle.status("member", "127.0.0.1"))
                .thenReturn(new LoginThrottleStatus(3, null));

        Document page = render(get("/login")
                .cookie(localeCookie(cookie))
                .param("error", "true")
                .session(sessionWith(new LoginFormState("member", 3, null))));

        assertThat(page.selectFirst(".login-inline-message__detail").text()).isEqualTo(expected);
        assertThat(page.selectFirst(".login-inline-message__title").text()).isNotBlank();
        // username 보존은 그대로다.
        assertThat(page.selectFirst("#username").val()).isEqualTo("member");
    }

    /** 잠금 안내는 countdown 요소를 유지하고 남은 시간을 현재 언어로 채운다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 10초",
            "en    | 10 sec",
            "ja    | 10秒",
            "zh-CN | 10 秒",
            "zh-TW | 10 秒"
    })
    void lockedNoticeKeepsTheCountdownElementAndLocalizesTheRemainingTime(
            String cookie, String expectedRemaining) throws Exception {
        Instant blockedUntil = Instant.now().plusSeconds(10);
        when(loginThrottle.status("member", "127.0.0.1"))
                .thenReturn(new LoginThrottleStatus(5, blockedUntil));

        Document page = render(get("/login")
                .cookie(localeCookie(cookie))
                .session(sessionWith(new LoginFormState("member", 5, blockedUntil))));

        assertThat(page.selectFirst("#loginThrottleCountdown").text())
                .isEqualTo(expectedRemaining);
        assertThat(page.selectFirst(".login-feedback__detail").text())
                .contains(expectedRemaining)
                .doesNotContain("{0}");
        assertThat(page.selectFirst(".login-submit").hasAttr("disabled")).isTrue();

        // JS 는 번역 객체를 갖지 않고 현재 locale 문구를 data-* 로 받는다.
        var feedback = page.selectFirst("#loginFailureFeedback");
        assertThat(feedback.attr("data-released-message")).isNotBlank();
        assertThat(feedback.attr("data-remaining-seconds-format")).contains("{0}");
        assertThat(feedback.attr("data-remaining-minutes-format")).contains("{0}");
        assertThat(feedback.attr("data-remaining-minutes-seconds-format"))
                .contains("{0}").contains("{1}");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 비밀번호가 변경되었습니다. 다시 로그인해주세요.",
            "en    | Your password has been changed. Please log in again.",
            "ja    | パスワードを変更しました。もう一度ログインしてください。",
            "zh-CN | 密码已修改，请重新登录。",
            "zh-TW | 密碼已變更，請重新登入。"
    })
    void passwordChangedNoticeIsLocalized(String cookie, String expected) throws Exception {
        Document page = render(get("/login")
                .cookie(localeCookie(cookie))
                .param("passwordChanged", "true"));

        assertThat(page.selectFirst(".login-feedback--success").text()).isEqualTo(expected);
    }

    /** 로그인 화면에도 사이트 헤더와 같은 언어 선택 메뉴가 있고, 현재 언어를 native name 으로 보여준다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 한국어",
            "en    | English",
            "ja    | 日本語",
            "zh-CN | 简体中文",
            "zh-TW | 繁體中文"
    })
    void loginPageShowsTheSharedLanguageMenuWithTheCurrentNativeName(String cookie,
                                                                    String nativeName)
            throws Exception {
        Document page = render(get("/login").cookie(localeCookie(cookie)));

        var menu = page.selectFirst(".language-menu");
        assertThat(menu).isNotNull();
        assertThat(menu.selectFirst(".language-menu-current").text()).isEqualTo(nativeName);
        assertThat(menu.selectFirst(".language-menu-icon")).isNotNull();
        // 5개 언어가 모두 native name 으로 나오고 현재 언어에만 체크가 붙는다.
        assertThat(menu.select(".language-menu-option > span:first-child").eachText())
                .containsExactly("한국어", "English", "日本語", "简体中文", "繁體中文");
        assertThat(menu.text()).doesNotContain("Language");
        assertThat(menu.select(".language-menu-option[aria-current=true] > span:first-child").text())
                .isEqualTo(nativeName);
        assertThat(menu.select(".language-menu-check")).hasSize(1);
        // 기존 POST /locale 흐름을 그대로 쓴다.
        // (CSRF 주입은 security filter 가 필요해 HeaderProfileMenuTest / LocaleControllerTest 가 본다.)
        assertThat(menu.select("form.locale-option-form[action=/locale][method=post]")).hasSize(5);
        assertThat(menu.select("form.locale-option-form input[name=languageTag]").eachAttr("value"))
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
    }

    /** 카드 안이 아니라 화면 우측 상단에 독립적으로 놓인다. */
    @Test
    void languageMenuSitsInTheTopBarOutsideTheLoginCard() throws Exception {
        Document page = render(get("/login").cookie(localeCookie("ko")));

        assertThat(page.selectFirst(".login-page__topbar .language-menu")).isNotNull();
        assertThat(page.select(".login-container .language-menu")).isEmpty();
        assertThat(page.select(".login-form .language-menu")).isEmpty();
        // 로그인 화면은 전체 사이트 헤더를 들이지 않는다.
        assertThat(page.select("header.site-header, #site-menu, #auth-area")).isEmpty();
        assertThat(page.select(".language-menu")).hasSize(1);
    }

    /**
     * 언어를 바꿔도 복귀 경로를 잃지 않도록 returnTo 가 현재 URL(쿼리 포함)을 담는다.
     * 로그인 폼 자체의 redirect hidden input 도 그대로 유지된다.
     */
    @Test
    void languageMenuCarriesTheCurrentUrlSoTheRedirectSurvives() throws Exception {
        // 실제 요청처럼 쿼리 문자열을 가진 URL 로 들어간다.
        Document page = render(get(URI.create("/login?redirect=%2Fcourse%2F9"))
                .cookie(localeCookie("ko")));

        assertThat(page.select("form.locale-option-form input[name=returnTo]").eachAttr("value"))
                .hasSize(5)
                .allSatisfy(returnTo -> assertThat(returnTo)
                        .isEqualTo("/login?redirect=%2Fcourse%2F9"));
        // 언어 선택 UI 가 로그인 폼의 복귀 경로를 건드리지 않는다.
        assertThat(page.selectFirst(".login-form input[name=redirect]").val())
                .isEqualTo("/course/9");
    }

    /** 비밀번호 표시/숨김 라벨도 JS 하드코딩 대신 data-* 로 전달된다. */
    @Test
    void passwordToggleLabelsComeFromTheBundle() throws Exception {
        Document page = render(get("/login").cookie(localeCookie("en")));

        var toggle = page.selectFirst(".toggle-password");
        assertThat(toggle.attr("data-show-label")).isEqualTo("Show password");
        assertThat(toggle.attr("data-hide-label")).isEqualTo("Hide password");
        assertThat(toggle.attr("aria-label")).isEqualTo("Show password");
    }

    private Document render(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Cookie localeCookie(String languageTag) {
        return new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, languageTag);
    }

    private MockHttpSession sessionWith(LoginFormState state) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginFormState.SESSION_ATTRIBUTE, state);
        return session;
    }
}
