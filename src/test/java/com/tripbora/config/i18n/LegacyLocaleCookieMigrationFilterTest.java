package com.tripbora.config.i18n;

import com.tripbora.controller.LocaleController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class LegacyLocaleCookieMigrationFilterTest {

    private static final String LEGACY = LegacyLocaleCookieMigrationFilter.LEGACY_COOKIE_NAME;
    private static final String CURRENT = TripBoraLocaleResolver.COOKIE_NAME;

    private final TripBoraLocaleResolver resolver = new TripBoraLocaleResolver();
    private final MockMvc mockMvc = standaloneSetup(
            new LocaleProbeController(), new LocaleController(resolver))
            .addFilters(new LegacyLocaleCookieMigrationFilter(resolver))
            .setLocaleResolver(resolver)
            .build();

    @Test
    void newCookieNameIsTripBoraLocale() {
        assertThat(CURRENT).isEqualTo("TRIPBORA_LOCALE");
        assertThat(LEGACY).isNotEqualTo(CURRENT);
    }

    /** 이전 쿠키만 있으면 이번 요청에 그 언어를 쓰고, 같은 값으로 새 쿠키를 발급한 뒤 이전 쿠키를 지운다. */
    @ParameterizedTest
    @ValueSource(strings = {"ko", "en", "ja", "zh-CN", "zh-TW"})
    void legacyOnlyIsAppliedNowAndMovedToTheNewCookie(String languageTag) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/locale-probe")
                        .cookie(new Cookie(LEGACY, languageTag))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR"))
                .andExpect(status().isOk())
                .andExpect(content().string(languageTag))
                .andReturn().getResponse();

        assertThat(setCookies(response, CURRENT)).singleElement().satisfies(cookie -> assertThat(cookie)
                .startsWith(CURRENT + "=" + languageTag + ";")
                .contains("Path=/", "Max-Age=31536000", "HttpOnly", "SameSite=Lax"));
        assertLegacyExpired(response);

        // 옮겨 받은 새 쿠키만으로도 다음 방문의 언어가 유지된다.
        mockMvc.perform(get("/locale-probe")
                        .cookie(new Cookie(CURRENT, languageTag))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR"))
                .andExpect(content().string(languageTag));
    }

    /** 새 쿠키와 이전 쿠키가 함께 있으면 새 쿠키가 이기고, 새 쿠키는 다시 쓰지 않으며 이전 쿠키만 지운다. */
    @Test
    void currentCookieWinsAndOnlyTheLegacyCookieIsExpired() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/locale-probe")
                        .cookie(new Cookie(CURRENT, "en"), new Cookie(LEGACY, "ja")))
                .andExpect(content().string("en"))
                .andReturn().getResponse();

        assertThat(setCookies(response, CURRENT)).isEmpty();
        assertLegacyExpired(response);
    }

    /** 이전 쿠키 값을 해석할 수 없으면 이전 쿠키만 지우고 기존 기본 언어 판단(Accept-Language)을 따른다. */
    @ParameterizedTest
    @ValueSource(strings = {"fr", "xx-YY", "-", ""})
    void invalidLegacyValueIsOnlyExpired(String legacyValue) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/locale-probe")
                        .cookie(new Cookie(LEGACY, legacyValue))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(content().string("ja"))
                .andReturn().getResponse();

        assertThat(setCookies(response, CURRENT)).isEmpty();
        assertLegacyExpired(response);
    }

    /** 관리자 화면은 계속 한국어로 고정되고, 공개 화면용 언어 선택은 새 쿠키로 옮겨진다. */
    @Test
    void adminStaysKoreanWhileThePublicChoiceIsMigrated() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/admin/locale-probe")
                        .cookie(new Cookie(LEGACY, "en")))
                .andExpect(content().string("ko"))
                .andReturn().getResponse();

        assertThat(setCookies(response, CURRENT)).singleElement()
                .satisfies(cookie -> assertThat(cookie).startsWith(CURRENT + "=en;"));
        assertLegacyExpired(response);

        mockMvc.perform(get("/admin/locale-probe").cookie(new Cookie(CURRENT, "en")))
                .andExpect(content().string("ko"));
        mockMvc.perform(get("/locale-probe").cookie(new Cookie(CURRENT, "en")))
                .andExpect(content().string("en"));
    }

    /** 새 쿠키만 있는 정상 경로와 쿠키가 없는 첫 방문에는 쿠키를 건드리지 않는다. */
    @Test
    void currentOnlyAndFirstVisitDoNotTouchCookies() throws Exception {
        MockHttpServletResponse currentOnly = mockMvc.perform(get("/locale-probe")
                        .cookie(new Cookie(CURRENT, "zh-TW")))
                .andExpect(content().string("zh-TW"))
                .andReturn().getResponse();
        assertThat(currentOnly.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();

        MockHttpServletResponse firstVisit = mockMvc.perform(get("/locale-probe")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(content().string("en"))
                .andReturn().getResponse();
        assertThat(firstVisit.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    /** POST /locale 은 새 쿠키에만 저장하고, 이전 쿠키가 남아 있으면 지운다. 마지막 새 쿠키가 고른 언어다. */
    @Test
    void localeChangeStoresOnlyTheNewCookieAndExpiresTheLegacyOne() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/locale")
                        .cookie(new Cookie(LEGACY, "en"))
                        .param("languageTag", "ja")
                        .param("returnTo", "/"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse();

        assertThat(setCookies(response, CURRENT)).last()
                .satisfies(cookie -> assertThat(cookie).startsWith(CURRENT + "=ja;"));
        assertLegacyExpired(response);

        MockHttpServletResponse withoutLegacy = mockMvc.perform(post("/locale")
                        .param("languageTag", "zh-CN")
                        .param("returnTo", "/"))
                .andReturn().getResponse();
        assertThat(withoutLegacy.getHeaders(HttpHeaders.SET_COOKIE)).singleElement()
                .satisfies(cookie -> assertThat(cookie).startsWith(CURRENT + "=zh-CN;"));
    }

    /** 공개 캐시가 붙는 정적 리소스 응답에는 쿠키를 싣지 않는다. */
    @Test
    void staticResourcesAreNotMigrated() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/css/site.css")
                        .cookie(new Cookie(LEGACY, "ja")))
                .andReturn().getResponse();

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    private static List<String> setCookies(MockHttpServletResponse response, String name) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .toList();
    }

    private static void assertLegacyExpired(MockHttpServletResponse response) {
        assertThat(setCookies(response, LEGACY)).singleElement().satisfies(cookie -> assertThat(cookie)
                .startsWith(LEGACY + "=;")
                .contains("Path=/", "Max-Age=0", "HttpOnly", "SameSite=Lax"));
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
