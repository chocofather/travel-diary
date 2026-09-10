package com.example.travlediary.config.i18n;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TravelDiaryLocaleResolverTest {

    private final TravelDiaryLocaleResolver resolver = new TravelDiaryLocaleResolver();

    /** 첫 방문(쿠키 없음)은 브라우저 Accept-Language 로 결정한다. */
    @ParameterizedTest
    @CsvSource({
            "ko-KR, ko",
            "ko, ko",
            "en-US, en",
            "en-GB, en",
            "ja-JP, ja",
            "ja, ja",
            "zh-CN, zh-CN",
            "zh-SG, zh-CN",
            "zh-Hans, zh-CN",
            "zh, zh-CN",
            "zh-TW, zh-TW",
            "zh-HK, zh-TW",
            "zh-MO, zh-TW",
            "zh-Hant, zh-TW"
    })
    void firstVisitFollowsTheBrowserPreferredLanguage(String acceptLanguage, String expectedTag) {
        assertThat(resolver.resolveLocale(publicRequest(null, acceptLanguage)))
                .isEqualTo(Locale.forLanguageTag(expectedTag));
    }

    /** 지원하지 않는 언어와 헤더 없음은 모두 English 로 떨어진다. */
    @ParameterizedTest
    @ValueSource(strings = {"fr-FR", "de", "es-ES", "*", "not a header"})
    void unsupportedBrowserLanguagesFallBackToEnglish(String acceptLanguage) {
        assertThat(resolver.resolveLocale(publicRequest(null, acceptLanguage)))
                .isEqualTo(Locale.ENGLISH);
    }

    /**
     * Accept-Language 를 아예 보내지 않는 요청은 선호 언어가 없는 것이므로
     * 지원하지 않는 언어(→English)와 달리 사이트 기본 언어인 한국어를 유지한다.
     */
    @Test
    void requestsWithoutAnyAcceptLanguageKeepTheSiteDefault() {
        assertThat(resolver.resolveLocale(publicRequest(null, null)))
                .isEqualTo(Locale.KOREAN);
        assertThat(resolver.resolveLocale(publicRequest(null, "  ")))
                .isEqualTo(Locale.KOREAN);
    }

    /** q 값이 있으면 선호 순서를 따르고, 지원하는 언어 중 가장 앞선 것을 고른다. */
    @Test
    void weightedAcceptLanguageUsesThePreferredSupportedEntry() {
        assertThat(resolver.resolveLocale(publicRequest(null, "fr-FR,de;q=0.9,ja;q=0.8,en;q=0.7")))
                .isEqualTo(Locale.forLanguageTag("ja"));
        assertThat(resolver.resolveLocale(publicRequest(null, "en-US,ko-KR;q=0.9")))
                .isEqualTo(Locale.ENGLISH);
    }

    /** 사용자가 직접 고른 언어(쿠키)는 언제나 브라우저 언어보다 우선한다. */
    @ParameterizedTest
    @CsvSource({
            "ja, en-US, ja",
            "zh-TW, ko-KR, zh-TW",
            "ko, en-US, ko",
            "en, ko-KR, en",
            "zh-CN, ja-JP, zh-CN"
    })
    void savedChoiceWinsOverTheBrowserLanguage(String cookie, String acceptLanguage,
                                               String expectedTag) {
        assertThat(resolver.resolveLocale(publicRequest(cookie, acceptLanguage)))
                .isEqualTo(Locale.forLanguageTag(expectedTag));
    }

    @Test
    void resolvesEverySupportedCookieValue() {
        assertThat(resolver.resolveLocale(publicRequest("en"))).isEqualTo(Locale.ENGLISH);
        assertThat(resolver.resolveLocale(publicRequest("ja")))
                .isEqualTo(Locale.forLanguageTag("ja"));
        assertThat(resolver.resolveLocale(publicRequest("zh-CN")))
                .isEqualTo(Locale.forLanguageTag("zh-CN"));
        assertThat(resolver.resolveLocale(publicRequest("zh-TW")))
                .isEqualTo(Locale.forLanguageTag("zh-TW"));
    }

    /** 손상된 쿠키 값은 저장된 선택이 없는 것으로 보고 브라우저 감지로 넘긴다. */
    @Test
    void unreadableCookieValuesFallBackToBrowserDetection() {
        assertThat(resolver.resolveLocale(publicRequest("fr", "ja-JP")))
                .isEqualTo(Locale.forLanguageTag("ja"));
        assertThat(resolver.resolveLocale(publicRequest("abc", "ko-KR")))
                .isEqualTo(Locale.forLanguageTag("ko"));
        assertThat(resolver.resolveLocale(publicRequest("abc", "fr-FR")))
                .isEqualTo(Locale.ENGLISH);
    }

    /** 지역만 붙은 쿠키 값도 같은 정규화를 거친다. */
    @Test
    void cookieValuesAreNormalizedThroughTheSameRules() {
        assertThat(resolver.resolveLocale(publicRequest("zh", "ko-KR")))
                .isEqualTo(Locale.forLanguageTag("zh-CN"));
        assertThat(resolver.resolveLocale(publicRequest("zh-HK", "ko-KR")))
                .isEqualTo(Locale.forLanguageTag("zh-TW"));
        assertThat(resolver.resolveLocale(publicRequest("en-GB", "ko-KR")))
                .isEqualTo(Locale.ENGLISH);
    }

    /** dropdown 선택 → 쿠키 갱신 → 재접속 시에도 브라우저 언어를 덮어쓰지 않고 선택이 유지된다. */
    @Test
    void aChosenLanguageIsStoredAndSurvivesLaterVisits() {
        MockHttpServletRequest firstVisit = publicRequest(null, "en-US");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(resolver.resolveLocale(firstVisit)).isEqualTo(Locale.ENGLISH);

        resolver.setLocale(firstVisit, response, SupportedLanguage.JAPANESE.getLocale());

        Cookie stored = response.getCookie(TravelDiaryLocaleResolver.COOKIE_NAME);
        assertThat(stored).isNotNull();
        assertThat(stored.getValue()).isEqualTo("ja");
        assertThat(resolver.resolveLocale(publicRequest(stored.getValue(), "en-US")))
                .isEqualTo(Locale.forLanguageTag("ja"));

        // 다른 언어로 다시 바꾸면 기존 쿠키 값이 정상 갱신된다.
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        MockHttpServletRequest laterVisit = publicRequest(stored.getValue(), "en-US");
        resolver.setLocale(laterVisit, secondResponse,
                SupportedLanguage.CHINESE_TRADITIONAL.getLocale());

        assertThat(secondResponse.getCookie(TravelDiaryLocaleResolver.COOKIE_NAME).getValue())
                .isEqualTo("zh-TW");
    }

    @Test
    void adminRenderingIsKoreanWithoutChangingThePublicCookieChoice() throws Exception {
        MockHttpServletRequest adminRequest = request("/admin/destinations", "en");

        assertThat(resolver.resolveLocale(adminRequest)).isEqualTo(Locale.KOREAN);
        assertThat(resolver.resolveLocaleContext(adminRequest).getLocale())
                .isEqualTo(Locale.KOREAN);

        MockHttpServletRequest publicRequest = request("/destinations/15", "en");
        assertThat(resolver.resolveLocale(publicRequest)).isEqualTo(Locale.ENGLISH);

        MockMvc mockMvc = standaloneSetup(new LocaleProbeController())
                .setLocaleResolver(resolver)
                .build();
        Cookie englishCookie = new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, "en");
        mockMvc.perform(get("/admin/locale-probe").cookie(englishCookie))
                .andExpect(status().isOk())
                .andExpect(content().string("ko"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mockMvc.perform(get("/locale-probe").cookie(englishCookie))
                .andExpect(status().isOk())
                .andExpect(content().string("en"));
    }

    private MockHttpServletRequest publicRequest(String languageTag) {
        return request("/destinations/15", languageTag);
    }

    private MockHttpServletRequest publicRequest(String languageTag, String acceptLanguage) {
        MockHttpServletRequest request = request("/destinations/15", languageTag);
        if (acceptLanguage != null) {
            request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        return request;
    }

    private MockHttpServletRequest request(String requestUri, String languageTag) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        if (languageTag != null) {
            request.setCookies(new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, languageTag));
        }
        return request;
    }

    @Controller
    private static class LocaleProbeController {

        @GetMapping({"/locale-probe", "/admin/locale-probe"})
        @ResponseBody
        String locale(Locale locale) {
            return locale.toLanguageTag();
        }
    }
}
