package com.example.travlediary.config.i18n;

import com.example.travlediary.controller.user.SocialSignupController;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.service.user.SocialSignupAuthenticationService;
import com.example.travlediary.service.user.SocialSignupService;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소셜 회원가입 화면의 5개 언어 계약.
 * 브랜드명은 번역하지 않고, 닉네임 문구는 일반 회원가입과 같은 공용 fragment 로 내려간다.
 */
@WebMvcTest(SocialSignupController.class)
@Import(I18nConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class SocialSignupPageI18nContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SocialSignupService socialSignupService;
    @MockitoBean
    private SocialSignupAuthenticationService authenticationService;
    @MockitoBean
    private com.example.travlediary.repository.user.UserMapper userMapper;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | ko    | 가입을 완료해주세요            | 닉네임      | 자동 추천   | 가입하고 시작하기 | 필수",
            "en    | en    | Finish creating your account | Nickname   | Suggest one| Sign up and start| Required",
            "ja    | ja    | 登録を完了してください         | ニックネーム | 自動提案    | 登録して始める    | 必須",
            "zh-CN | zh-CN | 请完成注册                   | 昵称        | 随机推荐    | 注册并开始       | 必选",
            "zh-TW | zh-TW | 請完成註冊                   | 暱稱        | 隨機建議    | 註冊並開始       | 必填"
    })
    void socialSignupRendersInEverySupportedLanguage(String cookie, String expectedLang,
                                                     String title, String nickname,
                                                     String suggest, String submit,
                                                     String required) throws Exception {
        Document page = render(cookie);

        assertThat(page.selectFirst("html").attr("lang")).isEqualTo(expectedLang);
        assertThat(page.title()).isNotBlank().doesNotContain("??");
        assertThat(page.selectFirst("#socialSignupTitle").text()).isEqualTo(title);
        assertThat(page.selectFirst("label[for=nickname]").text()).isEqualTo(nickname);
        assertThat(page.selectFirst("#generateNickname").text()).isEqualTo(suggest);
        assertThat(page.selectFirst(".login-submit").text()).isEqualTo(submit);
        assertThat(page.select(".social-signup__consents em").first().text())
                .isEqualTo("[" + required + "]");
        // 설명 / 도움말 / 안내도 번들에서 온다.
        assertThat(page.selectFirst(".login-header p").text()).isNotBlank();
        assertThat(page.selectFirst("#nicknameMessage").text()).isNotBlank();
        assertThat(page.selectFirst(".social-signup__notice").text()).isNotBlank();
        assertThat(page.selectFirst("#nickname").attr("placeholder")).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({"ko", "en", "ja", "zh-CN", "zh-TW"})
    void noUnresolvedMessageOrPlaceholderSurvives(String cookie) throws Exception {
        String html = mockMvc.perform(get("/social-signup")
                        .session(pendingSession()).cookie(localeCookie(cookie)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).as("unresolved message in %s", cookie).doesNotContain("??");
        assertThat(Jsoup.parse(html).selectFirst(".social-signup").text())
                .doesNotContain("{0}").doesNotContain("{1}");
    }

    /** 브랜드명은 어느 언어에서도 번역하지 않는다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | Google",
            "en    | Google",
            "ja    | Google",
            "zh-CN | Google",
            "zh-TW | Google"
    })
    void providerBrandNameIsNeverTranslated(String cookie, String brand) throws Exception {
        assertThat(render(cookie).selectFirst(".social-signup__account span").text())
                .contains(brand);
    }

    /** 일반 회원가입과 같은 언어 selector / 닉네임 문구 fragment 를 재사용한다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 한국어",
            "en    | English",
            "ja    | 日本語",
            "zh-CN | 简体中文",
            "zh-TW | 繁體中文"
    })
    void sharedLanguageMenuAndNicknameMessagesAreReused(String cookie, String nativeName)
            throws Exception {
        Document page = render(cookie);

        var menu = page.selectFirst(".login-page__topbar .language-menu");
        assertThat(menu).isNotNull();
        assertThat(menu.selectFirst(".language-menu-current").text()).isEqualTo(nativeName);
        assertThat(menu.select("form.locale-option-form[action=/locale][method=post]")).hasSize(5);

        var nicknameMessages = page.selectFirst("#nickname-messages");
        assertThat(nicknameMessages).isNotNull();
        for (String attribute : new String[]{"data-checking", "data-available", "data-taken",
                "data-forbidden", "data-invalid", "data-check-failed", "data-generate-failed"}) {
            assertThat(nicknameMessages.attr(attribute))
                    .as("%s in %s", attribute, cookie).isNotBlank();
        }
        // 추천은 기존 공용 API 를 그대로 쓴다.
        assertThat(page.select("script[src='/js/nickname-availability.js']")).isNotEmpty();
    }

    /** 두 화면이 같은 닉네임 정책(2~16자)과 같은 문구 fragment 를 쓴다. */
    @Test
    void standardAndSocialSignupShareTheSameNicknameContract() throws Exception {
        String register = read("src/main/resources/templates/register.html");
        String social = read("src/main/resources/templates/social-signup.html");

        for (String template : new String[]{register, social}) {
            assertThat(template).contains("maxlength=\"16\"");
            assertThat(template).contains("~{fragments/nickname-messages :: nicknameMessages}");
            assertThat(template).contains("#{signup.field.nickname.help}");
        }
        assertThat(read("src/main/resources/static/js/nickname-availability.js"))
                .contains("{2,16}");
    }

    /** 템플릿이 쓰는 message key 가 5개 번들에 모두 존재한다. */
    @Test
    void everyMessageKeyUsedBySocialSignupExistsInAllFiveBundles() throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        for (String template : new String[]{
                "src/main/resources/templates/social-signup.html",
                "src/main/resources/templates/fragments/nickname-messages.html"}) {
            Matcher matcher = Pattern.compile("#\\{([A-Za-z0-9._]+)").matcher(read(template));
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
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

    private Document render(String cookie) throws Exception {
        return Jsoup.parse(mockMvc.perform(get("/social-signup")
                        .session(pendingSession()).cookie(localeCookie(cookie)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private MockHttpSession pendingSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE, new PendingSocialSignup(
                "flow-123", SocialProvider.GOOGLE, "google-sub", "travel@example.com",
                true, Instant.now().minusSeconds(10), Instant.now().plusSeconds(590)));
        return session;
    }

    private Cookie localeCookie(String languageTag) {
        return new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, languageTag);
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
