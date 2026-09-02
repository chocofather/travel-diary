package com.example.travlediary.config.i18n;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
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

    @Test
    void defaultsToKoreanWithoutACookie() {
        assertThat(resolver.resolveLocale(publicRequest(null)))
                .isEqualTo(Locale.forLanguageTag("ko"));
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

    @Test
    void unsupportedCookieValuesFallBackToKorean() {
        assertThat(resolver.resolveLocale(publicRequest("fr")))
                .isEqualTo(Locale.KOREAN);
        assertThat(resolver.resolveLocale(publicRequest("abc")))
                .isEqualTo(Locale.KOREAN);
        assertThat(resolver.resolveLocale(publicRequest("zh")))
                .isEqualTo(Locale.KOREAN);
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
