package com.example.travlediary.controller.destination;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지역 목록 링크가 실제로 어떤 href 로 렌더되는지 확인한다.
 * 쿼리스트링을 문자열로 이어 붙이면 "&amp;region=" 이 HTML 엔티티 &amp;reg; 로 해석돼
 * region 파라미터가 통째로 사라진다.
 */
class RegionLinkRenderingTest {

    private final TemplateEngine engine = newEngine();

    @Test
    void concatenatedAmpersandIsParsedAsTheRegEntityAndLosesTheRegionParameter() {
        String html = render("<a th:href=\"@{'destinations?type=' + ${type} + '&region=' + ${regionId}}\">x</a>");

        // &region= → ® + ion= 로 깨져서 region 파라미터가 남지 않는다
        assertThat(html).contains("®ion=235");
        assertThat(html).doesNotContain("type=domestic&amp;region=235");
    }

    @Test
    void thymeleafParameterSyntaxKeepsBothFilterParameters() {
        String html = render("<a th:href=\"@{destinations(type=${type},region=${regionId})}\">x</a>");

        assertThat(html).contains("destinations?type=domestic&amp;region=235");
    }

    private String render(String template) {
        Context context = new Context();
        context.setVariable("type", "domestic");
        context.setVariable("regionId", 235L);
        return engine.process(template, context).trim();
    }

    private TemplateEngine newEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        // 프로젝트와 동일하게 SpEL 기반 Spring 다이얼렉트를 쓴다
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }
}
