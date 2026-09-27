package com.example.travlediary.seo;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeoController.class)
@Import(SecurityConfig.class)
class SeoControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SitemapService sitemapService;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @Test
    void anonymousUserCanLoadBrandingAssetsWithoutRedirectingToLogin() throws Exception {
        for (String path : List.of("/favicon.ico", "/favicon-16x16.png", "/favicon-32x32.png",
                "/apple-touch-icon.png", "/android-chrome-192x192.png",
                "/android-chrome-512x512.png", "/safari-pinned-tab.svg", "/site.webmanifest")) {
            mockMvc.perform(get(path).param("v", "20260928-1"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void anonymousUserCanReadRobotsAndSitemap() throws Exception {
        when(sitemapService.canonicalPaths()).thenReturn(List.of("/"));

        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sitemap:")));
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<urlset")));
    }
}
