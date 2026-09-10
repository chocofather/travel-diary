package com.example.travlediary.controller;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.I18nConfig;
import com.example.travlediary.config.i18n.TravelDiaryLocaleResolver;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
                        containsString(TravelDiaryLocaleResolver.COOKIE_NAME + "=zh-CN")))
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
                        containsString(TravelDiaryLocaleResolver.COOKIE_NAME + "=ko")));
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
                        containsString(TravelDiaryLocaleResolver.COOKIE_NAME + "=ja")));
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

    @Test
    void localeChangeKeepsCsrfProtection() throws Exception {
        mockMvc.perform(post("/locale")
                        .param("languageTag", "en")
                        .param("returnTo", "/"))
                .andExpect(status().isForbidden());
    }
}
