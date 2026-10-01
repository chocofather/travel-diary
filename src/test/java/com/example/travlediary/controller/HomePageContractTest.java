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
                .doesNotContain("navBar.style.backgroundColor", "pastelColors")
                .doesNotContain("#e0ffe0", "#fff5cc", "#ffe0f0", "#e0f7fa")
                // 자동재생은 슬라이드가 두 장 이상일 때만 켠다. 0장이면 타이머 없이 영역을 숨긴다.
                .contains("autoplay: hasMultipleSlides && !reducedMotion ? { delay: 10000")
                .contains("sliderUi.hidden = !hasMultipleSlides", "effect: 'fade'")
                .contains("if (slideCount === 0)", "classList.add('is-empty')")
                .contains("swiper.slidePrev()", "swiper.slideNext()");
        assertThat(sliderCss)
                .contains("grid-template-columns: minmax(0, 35fr) minmax(0, 65fr)")
                .contains(".slider-ui[hidden]", "prefers-reduced-motion: reduce")
                .contains("aspect-ratio: 16 / 9")
                .contains("#event-slider #progress-bar")
                .contains("#event-slider .slide-text a.more")
                .contains("font-size: 26px")
                .contains("font-size: 13px");
        assertThat(homeScript)
                .contains("SPRING", "SUMMER", "FALL", "WINTER")
                .contains("/api/season-destinations?season=")
                .contains("renderSeasonDestinations(currentSeason, tag.id)")
                // 인기 여행지는 필터 없는 편집형 영역이라 서버가 그린다. 메인 스크립트는 필터 API 를 부르지 않는다.
                .doesNotContain("/api/popular-destinations/")
                .doesNotContain("renderPopularRecommend", "popularTags");
        assertThat(template)
                .contains("th:each=\"destination, stat : ${popularDestinations}\"")
                .contains("th:href=\"@{/destinations/{id}(id=${destination.id})}\"")
                .contains("th:alt=\"${destination.name}\"");
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
    void festivalsReplaceHomeCoursesWithManualRailAndExistingFestivalLinks() throws IOException {
        String template = resource("/templates/home.html");
        String homeCss = resource("/static/css/home-festival.css");

        assertThat(template)
                .contains("지금 가볼 만한 축제·행사")
                .contains("th:if=\"${!#lists.isEmpty(homeFestivals)}\"")
                .contains("th:each=\"festival : ${homeFestivals}\"")
                .contains("@{/festivals/{id}(id=${festival.id})}")
                .contains("@{/travel-info(contentType='FESTIVAL')}")
                .contains("home-festival-placeholder")
                .doesNotContain("popularCourses", "popular-course-section");
        assertThat(homeCss)
                .contains("calc((100% - 54px) / 4)", "calc((100% - 32px) / 3)")
                .contains("overflow-x: auto", "flex-basis: 55%", "aspect-ratio: 3 / 4")
                .doesNotContain("animation:");
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
                .contains("th:text=\"${festival.title}\"")
                .contains("th:text=\"${festival.location}\"");
        assertThat(homeScript)
                .contains("home-i18n", ".dataset", "homeI18n.springTitle")
                .contains("${dest.name}", "${regionText(dest)}")
                // 지역은 API 가 준 상위 지역 + 지역 이름을 이어 붙인다. (화면에 지역명을 박아 두지 않는다)
                .contains("[dest.parentRegionName, dest.regionName]")
                .doesNotContain("데이터가 없습니다.", "불러오기에 실패했습니다.");
        assertThat(sliderScript)
                .contains("home-i18n", ".dataset", "homeI18n.eventDetails", ">EVENT</span>")
                // 이벤트 값은 여전히 서버가 준 그대로 그린다. (escape 만 거친다)
                .contains("escapeHtml(ev.title)", "escapeHtml(ev.description)")
                .doesNotContain(">자세히 보기<");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
