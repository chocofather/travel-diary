package com.example.travlediary.repository;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationImageAttributionUiContractTest {

    @Test
    void detailOnlyCreatesTheQuietCaptionWhenAnyImageHasAttribution() throws IOException {
        String source = resource("/templates/destination/detail.html");
        Document page = Jsoup.parse(source);

        assertThat(page.select("[data-destination-image-attribution]")).hasSize(1);
        assertThat(source)
                .contains("th:if=\"${hasImageAttribution}\"")
                .contains("data-source-name=${img.sourceName}")
                .contains("data-license-label=${img.licenseDisplay}")
                .contains("data-photographer=${img.photographer}")
                .contains("data-source-url=${img.safeSourceUrl}");
        assertThat(page.select("[data-destination-image-source-link][target=_blank]"
                + "[rel='noopener noreferrer']")).hasSize(1);
    }

    @Test
    void carouselMovementUpdatesSourceLicenseAndSafeLinkTogether() throws IOException {
        String script = resource("/static/js/detail-carousel.js");
        String modalScript = resource("/static/js/destination-image-modal.js");

        assertThat(script)
                .contains("function updateAttribution()")
                .contains("slide?.dataset.sourceName")
                .contains("slide?.dataset.licenseLabel")
                .contains("slide?.dataset.photographer")
                .contains("slide?.dataset.sourceUrl")
                .contains("$source.text(sourceName)")
                .contains("$license.text(licenseLabel)")
                .contains("$photographer.text(photographer)")
                .contains("$sourceLink.attr('href', sourceUrl)")
                .contains("updateAttribution();")
                .contains("destination-gallery-change")
                .contains("move(0);");
        assertThat(modalScript)
                .contains("new CustomEvent('destination-gallery-change'")
                .contains("detail: {index: currentIndex}");
    }

    @Test
    void attributionStyleStaysSmallUnboxedAndCloseToTheImage() throws IOException {
        String css = resource("/static/css/detail.css");
        String rule = css.substring(css.indexOf(".destination-image-attribution {"),
                css.indexOf("}", css.indexOf(".destination-image-attribution {")) + 1);

        assertThat(rule)
                .contains("font-size: 11px")
                .contains("color: #9299a6")
                .contains("margin: 6px 2px 0")
                .doesNotContain("background", "border", "padding");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
