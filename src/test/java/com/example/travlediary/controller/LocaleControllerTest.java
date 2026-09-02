package com.example.travlediary.controller;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.I18nConfig;
import com.example.travlediary.config.i18n.TravelDiaryLocaleResolver;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.Test;
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

    @Test
    void localeChangeKeepsCsrfProtection() throws Exception {
        mockMvc.perform(post("/locale")
                        .param("languageTag", "en")
                        .param("returnTo", "/"))
                .andExpect(status().isForbidden());
    }
}
