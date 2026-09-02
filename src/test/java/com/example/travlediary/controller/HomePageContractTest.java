package com.example.travlediary.controller;

import com.example.travlediary.controller.recommend.RandomRecommendController;
import com.example.travlediary.service.recommend.RouletteRegionController;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HomePageContractTest {

    @Test
    void existingSliderSeasonAndPopularContractsRemain() throws IOException {
        String template = resource("/templates/home.html");
        String homeScript = resource("/static/js/home.js");
        String sliderScript = resource("/static/js/slider.js");
        String sliderCss = resource("/static/css/slider.css");
        var document = Jsoup.parse(template);

        assertThat(document.select("#event-slider .swiper #slide-area")).hasSize(1);
        assertThat(document.select(".slider-ui .prev, .slider-ui .pause, .slider-ui .next")).hasSize(3);
        assertThat(document.select("#progress-bar")).hasSize(1);
        assertThat(sliderScript)
                .contains("fetch('/api/events/slide')")
                .contains("navBar.style.backgroundColor = bgColor")
                .contains("autoplay: { delay: 10000")
                .contains("swiper.slidePrev()", "swiper.slideNext()");
        assertThat(sliderCss)
                .contains("padding-top: 150px")
                .contains("height: 400px")
                .contains("height: 160px")
                .contains("max-width: 500px")
                .contains("margin-left: 250px")
                .contains("#event-slider #progress-bar")
                .contains("#event-slider .slide-text a.more")
                .contains("padding: 6px 12px")
                .contains("font-size: 13px");
        assertThat(homeScript)
                .contains("SPRING", "SUMMER", "FALL", "WINTER")
                .contains("/api/season-destinations?season=")
                .contains("renderSeasonDestinations(currentSeason, tag.id)")
                .contains("/api/popular-destinations/domestic")
                .contains("/api/popular-destinations/overseas")
                .contains("/api/popular-destinations/history")
                .contains("/api/popular-destinations/photo")
                .contains("renderPopularRecommend(popularTags[0].api)");
    }

    @Test
    void rouletteLeavesHomeWhileBackendRoutesRemain() throws IOException {
        String template = resource("/templates/home.html");

        assertThat(template)
                .doesNotContain("instant-trip")
                .doesNotContain("roulette-canvas")
                .doesNotContain("/js/random.js");
        assertThat(RandomRecommendController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/random-recommend");
        assertThat(RouletteRegionController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/roulette-region");
    }

    @Test
    void popularCourseCardsKeepDetailUrlAndUseCenteredRoutePreview() throws IOException {
        String template = resource("/templates/home.html");
        String homeCss = resource("/static/css/home.css");

        assertThat(template)
                .contains("여행자들이 많이 본 코스")
                .contains("th:each=\"course : ${popularCourses}\"")
                .contains("th:href=\"${course.detailUrl}\"")
                .contains("th:each=\"destinationName, stopStatus : ${course.previewDestinationNames}\"")
                .contains("popular-course-route-dot")
                .contains("popular-course-route-connector")
                .contains("course.remainingDestinationCount > 0")
                .contains("course.totalDestinationCount");
        assertThat(homeCss)
                .contains(".popular-course-list")
                .contains("display: flex")
                .contains("justify-content: center")
                .contains("flex: 0 1 360px")
                .contains("white-space: nowrap")
                .contains("text-overflow: ellipsis");
    }

    @Test
    void mainScriptsReadLocalizedUiFromTheRenderedPageWithoutChangingApiContentBindings()
            throws IOException {
        String template = resource("/templates/home.html");
        String homeScript = resource("/static/js/home.js");
        String sliderScript = resource("/static/js/slider.js");

        assertThat(template)
                .contains("id=\"home-i18n\"")
                .contains("#{home.season.spring.title}")
                .contains("#{home.event.details}")
                .contains("th:text=\"${course.title}\"")
                .contains("th:text=\"${destinationName}\"");
        assertThat(homeScript)
                .contains("home-i18n", ".dataset", "homeI18n.springTitle", "homeI18n.popularTags")
                .contains("${dest.name}", "${dest.regionName}")
                .doesNotContain("데이터가 없습니다.", "불러오기에 실패했습니다.");
        assertThat(sliderScript)
                .contains("home-i18n", ".dataset", "homeI18n.eventDetails")
                .contains("${ev.title}", "${ev.description}")
                .doesNotContain(">자세히 보기<");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
