package com.example.travlediary.seo;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeoControllerTest {

    @Test
    void robotsAndSitemapUseAbsolutePublicUrls() throws Exception {
        SitemapService sitemapService = mock(SitemapService.class);
        when(sitemapService.canonicalPaths()).thenReturn(List.of("/", "/destinations/9"));
        SeoController controller = new SeoController(sitemapService, "https://travel.example");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Sitemap: https://travel.example/sitemap.xml")));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<loc>https://travel.example/destinations/9</loc>")));
    }
}
