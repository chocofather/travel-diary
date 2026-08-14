package com.example.travlediary.controller;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RandomTravelPageContractTest {

    @Test
    void drawInteractionUsesOneRequestAndAvoidsThePreviousRegion() throws IOException {
        String script = resource("/static/js/random-travel.js");

        assertThat(script)
                .contains("/api/random-recommend?")
                .contains("excludeRegionId")
                .contains("previousRegionIds")
                .contains("result.regionId")
                .contains("isDrawing")
                .contains("response.status === 204")
                .contains("setControlsDisabled")
                .contains("조건에 맞는 여행지를 찾지 못했어요.")
                .contains("여행지를 불러오지 못했어요.")
                .contains("다시 뽑기");
        assertThat(count(script, "fetch(")).isEqualTo(1);
    }

    @Test
    void resultRendersARegionHeroAndAtMostEightServerBackedDestinationCards()
            throws IOException {
        String script = resource("/static/js/random-travel.js");

        assertThat(script)
                .contains("result.countryName")
                .contains("result.regionName")
                .contains("result.recommendedDestinations")
                .contains("slice(0, 8)")
                .contains("destination.destinationName")
                .contains("destination.shortDescription")
                .contains("destination.imageUrl")
                .contains("destination.regionName")
                .contains("destination.detailUrl")
                .contains("이번에 떠날 곳은")
                .contains("에서 둘러볼 여행지")
                .contains("/images/default.png")
                .contains("document.createElement")
                .contains("textContent")
                .contains("replaceChildren")
                .contains("addEventListener('error'")
                .doesNotContain("innerHTML");
    }

    @Test
    void animationUsesTheSelectedRegionAndRespectsReducedMotion() throws IOException {
        String template = resource("/templates/random-travel.html");
        String script = resource("/static/js/random-travel.js");
        var document = Jsoup.parse(template);

        assertThat(document.select(".random-travel-decoration img[src='/images/random1.png']"))
                .hasSize(1);
        assertThat(document.select("canvas, .roulette-canvas")).isEmpty();
        assertThat(script)
                .contains("window.matchMedia('(prefers-reduced-motion: reduce)')")
                .contains("window.setInterval")
                .contains("1200")
                .contains("result.regionName");
    }

    @Test
    void cardGridAdaptsFromThreeColumnsToTwoAndOne() throws IOException {
        String css = resource("/static/css/random-travel.css");

        assertThat(css)
                .contains("grid-template-columns: repeat(3, minmax(0, 1fr))")
                .contains("@media (max-width: 900px)")
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
                .contains("@media (max-width: 720px)")
                .contains("grid-template-columns: 1fr")
                .contains("@media (prefers-reduced-motion: reduce)")
                .contains("object-fit: cover")
                .contains("-webkit-line-clamp: 3");
    }

    private int count(String source, String target) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
