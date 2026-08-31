package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FestivalPublicUiContractTest {

    @Test
    void festivalDetailUsesPublicLayoutLocalHeroImageStructuredInfoAndSafeAttribution() throws IOException {
        String template = resource("/templates/festivals/detail.html");

        assertThat(template)
                .contains("layout/main :: layout")
                .contains("/css/quill-content.css", "/css/festival-detail.css")
                .contains("festival-detail-hero", "festival-detail-title", "festival-detail-summary")
                .contains("th:src=\"@{${image.imageUrl}}\"")
                .contains("대표이미지가 없습니다")
                .contains("행사 소개", "festival-detail-content rich-text-content")
                .contains("th:utext=\"${festival.travelInfo.content}\"")
                .contains("행사 정보", "festival-detail-info-list")
                .contains("festival.primaryPeriod", "festival.eventPlace", "festival.address")
                .contains("festival.playTime", "festival.useTime")
                .contains("festival.sponsor1", "festival.sponsor2", "festival.contactTel")
                .contains("공식 홈페이지", "target=\"_blank\"", "rel=\"noopener noreferrer\"")
                .contains("사진 출처:", "festival.galleryImages[0].sourceName",
                        "festival.galleryImages[0].licenseLabel")
                .doesNotContain("festival.mainImage.sourceImageUrl", "source_image_url");
    }

    @Test
    void festivalCssKeepsHeroTextInfoAndAttributionResponsiveWithoutHeavyEffects() throws IOException {
        String css = resource("/static/css/festival-detail.css");

        assertThat(css)
                .contains("object-fit: cover")
                .contains("overflow-wrap: anywhere")
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
                .contains("@media (max-width: 760px)")
                .contains("@media (max-width: 520px)")
                .contains("grid-template-columns: minmax(0, 1fr)")
                .contains(".festival-detail-content pre")
                .contains("overflow-x: auto")
                .contains(".festival-detail-content [class*=\"ql-indent-\"]")
                .contains("padding-left: min(3em, 12vw)")
                .contains("max-width: 100%")
                .doesNotContain("linear-gradient", "animation:", "box-shadow:");
    }

    @Test
    void festivalInformationUsesTwoColumnsOnDesktopAndOneColumnOnMobile() throws IOException {
        String template = resource("/templates/festivals/detail.html");
        String css = resource("/static/css/festival-detail.css");

        assertThat(template)
                .contains("festival-detail-info-row is-wide")
                .doesNotContain("festival-detail-kicker");
        assertThat(css)
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
                .contains(".festival-detail-info-row.is-wide")
                .contains("grid-column: 1 / -1")
                .contains("@media (max-width: 720px)")
                .contains("grid-template-columns: minmax(0, 1fr)")
                .contains("overflow-wrap: anywhere");
    }

    @Test
    void festivalGalleryReusesDialogNavigationPatternAndSupportsKeyboardControls() throws IOException {
        String template = resource("/templates/festivals/detail.html");
        String script = resource("/static/js/festival-gallery.js");

        assertThat(template)
                .contains("data-festival-gallery", "data-festival-gallery-slide")
                .contains("data-festival-gallery-prev", "data-festival-gallery-next")
                .contains("festival-image-modal", "<dialog")
                .contains("data-license-label", "festival-detail-attribution")
                .doesNotContain("sourceImageUrl", "source_image_url");
        assertThat(script)
                .contains("ArrowLeft", "ArrowRight", "Escape")
                .contains("showModal", "currentIndex")
                .contains("(currentIndex + step + slides.length) % slides.length")
                .contains("updateAttribution", "updateCounter");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
