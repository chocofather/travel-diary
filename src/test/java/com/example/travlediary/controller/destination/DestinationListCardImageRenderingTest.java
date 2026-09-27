package com.example.travlediary.controller.destination;

import com.example.travlediary.dto.DestinationDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 여행지 목록 카드 이미지. 실제 fragment.html 의 {@code <img>} 를 그대로 렌더한다.
 * 여행지 업로드 사진은 썸네일(srcset/sizes)과 원본 되돌림을, 외부 주소는 원래 주소만 쓴다. lazy loading 은 둘 다 유지한다.
 */
class DestinationListCardImageRenderingTest {

    @Test
    void uploadedPhotosUseCardThumbnailsWithTheOriginalAsFallbackAndOtherAddressesStayAsTheyAre() throws IOException {
        DestinationDto uploaded = card("/uploads/destinations/c51f.jpg");
        uploaded.setCardImageUrl("/thumbnails/destinations/v2/480/c51f.jpg");
        uploaded.setCardImageSrcset("/thumbnails/destinations/v2/480/c51f.jpg 907w, "
                + "/thumbnails/destinations/v2/960/c51f.jpg 1814w");
        // 가로로 긴 사진: 4:3 칸을 채우려면 칸 폭의 2.18배로 그려진다.
        uploaded.setCardImageCoverScale(2.18);

        Element thumbnail = render(uploaded);
        assertThat(thumbnail.attr("src")).isEqualTo("/thumbnails/destinations/v2/480/c51f.jpg");
        assertThat(thumbnail.attr("srcset")).isEqualTo(uploaded.getCardImageSrcset());
        assertThat(thumbnail.attr("sizes")).isEqualTo("(max-width: 600px) calc(100vw * 2.18), "
                + "(max-width: 1024px) calc((50vw - 20px) * 2.18), (max-width: 1300px) calc((25vw - 28px) * 2.18), "
                + "calc(301px * 2.18)");
        assertThat(thumbnail.attr("data-original-src")).isEqualTo("/uploads/destinations/c51f.jpg");
        assertThat(thumbnail.attr("onerror")).contains("this.dataset.originalSrc");
        assertThat(thumbnail.attr("loading")).isEqualTo("lazy");

        Element external = render(card("https://tong.visitkorea.or.kr/cms/resource/a.jpg"));
        assertThat(external.attr("src")).isEqualTo("https://tong.visitkorea.or.kr/cms/resource/a.jpg");
        assertThat(external.hasAttr("srcset")).isFalse();
        assertThat(external.hasAttr("sizes")).isFalse();
        assertThat(external.hasAttr("data-original-src")).isFalse();
        assertThat(external.attr("loading")).isEqualTo("lazy");
    }

    private static DestinationDto card(String thumbnailPath) {
        DestinationDto card = new DestinationDto();
        card.setId(34L);
        card.setName("롯데호텔");
        card.setThumbnailPath(thumbnailPath);
        return card;
    }

    private Element render(DestinationDto card) throws IOException {
        MockServletContext servletContext = new MockServletContext();
        WebContext context = new WebContext(JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(new MockHttpServletRequest(servletContext), new MockHttpServletResponse()),
                Locale.KOREAN);
        context.setVariable("d", card);
        return Jsoup.parse(engine().process(cardImageTemplate(), context)).selectFirst("img");
    }

    /** fragment.html 의 목록 카드 {@code <img ... />} 한 개. */
    private String cardImageTemplate() throws IOException {
        String fragment;
        try (InputStream input = getClass().getResourceAsStream("/templates/destination/fragment.html")) {
            assertThat(input).isNotNull();
            fragment = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher image = Pattern.compile("<img th:if=\"\\$\\{d\\.thumbnailPath != null}\".*?/>", Pattern.DOTALL)
                .matcher(fragment);
        assertThat(image.find()).isTrue();
        return image.group();
    }

    private static SpringTemplateEngine engine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setFallbackToSystemLocale(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(messageSource);
        return engine;
    }
}
