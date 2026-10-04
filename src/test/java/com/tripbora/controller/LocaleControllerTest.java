package com.tripbora.controller;

import com.tripbora.config.CustomLoginSuccessHandler;
import com.tripbora.config.CustomLogoutSuccessHandler;
import com.tripbora.config.SecurityConfig;
import com.tripbora.config.i18n.I18nConfig;
import com.tripbora.config.i18n.LegacyLocaleCookieMigrationFilter;
import com.tripbora.config.i18n.TripBoraLocaleResolver;
import com.tripbora.repository.user.UserMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocaleController.class)
@Import({SecurityConfig.class, I18nConfig.class})
class LocaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @Test
    void guestCanSelectALanguageAndReturnToTheCurrentInternalPage() throws Exception {
        mockMvc.perform(post("/locale")
                        .with(csrf())
                        .param("languageTag", "zh-CN")
                        .param("returnTo", "/destinations/15?tab=info"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/destinations/15?tab=info"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString(TripBoraLocaleResolver.COOKIE_NAME + "=zh-CN")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    @Test
    void externalRedirectTargetFallsBackToHome() throws Exception {
        mockMvc.perform(post("/locale")
                        .with(csrf())
                        .param("languageTag", "en")
                        .param("returnTo", "https://evil.example/steal"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void unsupportedLanguageFallsBackToKorean() throws Exception {
        mockMvc.perform(post("/locale")
                        .with(csrf())
                        .param("languageTag", "fr")
                        .param("returnTo", "/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString(TripBoraLocaleResolver.COOKIE_NAME + "=ko")));
    }

    /**
     * 로그인 화면에서 언어를 바꿔도 복귀 경로(redirect)를 잃으면 안 된다.
     * returnTo 는 인코딩된 쿼리를 그대로 유지한 채 되돌아와야 한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/login?redirect=%2Fcourse%2F9",
            "/login?redirect=%2Fpost%2F13",
            "/login?redirect=%2Fdestinations%2F15"
    })
    void languageChangeOnTheLoginPageKeepsTheRedirectParameter(String loginUrl) throws Exception {
        mockMvc.perform(post("/locale")
                        .with(csrf())
                        .param("languageTag", "ja")
                        .param("returnTo", loginUrl))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(loginUrl))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString(TripBoraLocaleResolver.COOKIE_NAME + "=ja")));
    }

    /** 언어 변경 경로로도 외부 주소로는 나가지 않는다(open redirect 방어 유지). */
    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example/login?redirect=%2Fcourse%2F9",
            "//evil.example/login",
            "/login%2f?redirect=%2Fcourse%2F9",
            "not-an-internal-path"
    })
    void unsafeReturnToIsRejectedEvenWithARedirectParameter(String unsafe) throws Exception {
        mockMvc.perform(post("/locale")
                        .with(csrf())
                        .param("languageTag", "ja")
                        .param("returnTo", unsafe))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    /** 이전 이름의 쿠키를 가진 채 언어를 바꿔도 새 쿠키에만 저장되고 이전 쿠키는 지워진다. */
    @Test
    void localeChangeExpiresTheLegacyCookie() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/locale")
                        .with(csrf())
                        .cookie(new Cookie(LegacyLocaleCookieMigrationFilter.LEGACY_COOKIE_NAME, "en"))
                        .param("languageTag", "ja")
                        .param("returnTo", "/"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse();

        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies)
                .filteredOn(cookie -> cookie.startsWith(TripBoraLocaleResolver.COOKIE_NAME + "="))
                .last()
                .satisfies(cookie -> assertThat(cookie)
                        .startsWith(TripBoraLocaleResolver.COOKIE_NAME + "=ja;"));
        assertThat(setCookies)
                .filteredOn(cookie -> cookie.startsWith(
                        LegacyLocaleCookieMigrationFilter.LEGACY_COOKIE_NAME + "="))
                .singleElement()
                .satisfies(cookie -> assertThat(cookie).contains("Max-Age=0", "Path=/"));
    }

    @Test
    void localeChangeKeepsCsrfProtection() throws Exception {
        mockMvc.perform(post("/locale")
                        .param("languageTag", "en")
                        .param("returnTo", "/"))
                .andExpect(status().isForbidden());
    }
}
