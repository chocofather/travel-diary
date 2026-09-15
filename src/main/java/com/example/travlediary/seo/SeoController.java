package com.example.travlediary.seo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.stream.Collectors;

@RestController
public class SeoController {

    private static final String ROBOTS_RULES = """
            User-agent: *
            Allow: /
            Disallow: /admin
            Disallow: /mypage
            Disallow: /diaries
            Disallow: /travel-plans
            Disallow: /account
            Disallow: /api
            Disallow: /search
            Disallow: /uploads/diary-covers/
            Disallow: /uploads/diary-pages/
            Disallow: /uploads/diary-cover-designs/
            Disallow: /*/fragment
            Disallow: /*-fragment
            """;

    private final SitemapService sitemapService;
    private final String configuredSiteBaseUrl;

    public SeoController(SitemapService sitemapService,
                         @Value("${seo.site-base-url:}") String configuredSiteBaseUrl) {
        this.sitemapService = sitemapService;
        this.configuredSiteBaseUrl = configuredSiteBaseUrl;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots(HttpServletRequest request) {
        String baseUrl = SeoSiteUrl.resolveBaseUrl(configuredSiteBaseUrl, request);
        return ROBOTS_RULES + "Sitemap: " + baseUrl + "/sitemap.xml\n";
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap(HttpServletRequest request) {
        String baseUrl = SeoSiteUrl.resolveBaseUrl(configuredSiteBaseUrl, request);
        String urls = sitemapService.canonicalPaths().stream()
                .map(path -> SeoSiteUrl.absolute(baseUrl, path))
                .map(HtmlUtils::htmlEscape)
                .map(url -> "  <url><loc>" + url + "</loc></url>")
                .collect(Collectors.joining("\n"));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n"
                + urls + "\n</urlset>\n";
    }
}
