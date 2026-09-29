package com.example.travlediary.controller;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.I18nConfig;
import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.config.i18n.TravelDiaryLocaleResolver;
import com.example.travlediary.dto.HomePopularCourseDto;
import com.example.travlediary.dto.SeasonDestinationDto;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.course.CourseService;
import com.example.travlediary.service.file.DestinationCardThumbnailService;
import com.example.travlediary.service.recommend.DestinationRecommendService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = HomeController.class,
        properties = {"app.contact-email=contact@tripbora.test",
                "seo.site-base-url=https://tripbora.com"})
@Import({SecurityConfig.class, I18nConfig.class})
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private DestinationRecommendService recommendService;
    @MockitoBean
    private DestinationCardThumbnailService cardThumbnailService;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @Test
    void guestHomeRendersPopularCourseRouteAndExistingMainSections() throws Exception {
        HomePopularCourseDto course = new HomePopularCourseDto();
        course.setCourseId(12L);
        course.setTitle("서울 하루 고궁 산책");
        course.setNickname("minjun");
        course.setViews(1284);
        course.setTotalDestinationCount(5);
        course.setPreviewDestinationNames(List.of("경복궁", "북촌한옥마을", "창덕궁"));
        course.setPreviewImageUrls(List.of(
                "/images/gyeongbokgung.jpg",
                "/images/bukchon.jpg",
                "/images/changdeokgung.jpg"));
        when(courseService.getPopularCoursesForHome(SupportedLanguage.KOREAN))
                .thenReturn(List.of(course));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attribute("isLoggedIn", false))
                .andExpect(model().attribute("popularCourses", List.of(course)))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".footer-operations .footer-operator-name")).isEmpty();
                    assertThat(document.select(".footer-operations .footer-contact-email").text())
                            .isEqualTo("contact@tripbora.test");
                    assertThat(document.select("#event-slider #slide-area")).hasSize(1);
                    assertThat(document.select(".home-service-teaser")).hasSize(1);
                    assertThat(document.select(".home-service-teaser a[href='/about']")).hasSize(1);
                    // jsoup 의 Element 는 Iterable<Element> 라 assertThat(요소) 가 컬렉션 단언으로
                    // 잡힌다. 확인하려는 것은 "바로 다음 형제의 class" 하나이므로 그 값을 직접 본다.
                    assertThat(document.selectFirst("#event-slider").nextElementSibling()
                            .hasClass("seasonal-recommend")).isTrue();
                    // 랜드마크가 없으면 섹션을 그리지 않아 계절 추천 다음이 바로 서비스 소개다.
                    assertThat(document.select(".home-converge")).isEmpty();
                    assertThat(document.selectFirst(".seasonal-recommend").nextElementSibling()
                            .hasClass("home-service-teaser")).isTrue();
                    assertThat(document.selectFirst(".home-service-teaser").nextElementSibling()
                            .hasClass("popular-recommend")).isTrue();
                    assertThat(document.select(".seasonal-recommend")).hasSize(1);
                    assertThat(document.select(".popular-recommend")).hasSize(1);
                    assertThat(document.select("a.popular-course-card[href='/course/12']")).hasSize(1);
                    assertThat(document.select(".popular-course-visual.is-count-3 img")
                            .eachAttr("src")).containsExactly(
                                    "/images/gyeongbokgung.jpg",
                                    "/images/bukchon.jpg",
                                    "/images/changdeokgung.jpg");
                    assertThat(document.select(".popular-course-image-more").text()).isEqualTo("+2");
                    assertThat(document.select(".popular-course-route-track").text())
                            .isEqualTo("경복궁 → 북촌한옥마을 → 창덕궁");
                    assertThat(document.select(".popular-course-card").text())
                            .contains("서울 하루 고궁 산책")
                            .contains("minjun · 조회 1,284")
                            .contains("장소 5곳");
                    assertThat(document.select(".instant-trip, #roulette-canvas")).isEmpty();
                });

        verify(courseService).getPopularCoursesForHome(SupportedLanguage.KOREAN);
    }

    @Test
    void landmarkCardsLinkToDestinationsWithParentAndRegionNames() throws Exception {
        SeasonDestinationDto bigBen = landmark(31L, "빅벤",
                "https://upload.wikimedia.org/big-ben.jpg", "영국", "런던");
        SeasonDestinationDto palace = landmark(15L, "경복궁",
                "/uploads/destinations/palace.jpg", "서울", "종로구");
        // 썸네일 서비스가 채워 두는 값 (여기서는 서비스가 mock 이라 미리 넣어 둔다)
        palace.setCardImageUrl("/destination-thumbnails/v1/480/palace.jpg");
        palace.setCardImageSrcset("/destination-thumbnails/v1/480/palace.jpg 480w, "
                + "/destination-thumbnails/v1/960/palace.jpg 960w");
        palace.setCardImageCoverScale(1.78);
        SeasonDestinationDto jeju = landmark(40L, "성산일출봉",
                "/uploads/destinations/seongsan.jpg", null, "제주");
        List<SeasonDestinationDto> landmarks = List.of(bigBen, palace, jeju);
        when(recommendService.findHomeLandmarks(SupportedLanguage.KOREAN)).thenReturn(landmarks);

        mockMvc.perform(get("/").header("Accept-Language", "ko"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.selectFirst(".seasonal-recommend").nextElementSibling()
                            .hasClass("home-converge")).isTrue();
                    assertThat(document.selectFirst(".home-converge").nextElementSibling()
                            .hasClass("home-service-teaser")).isTrue();
                    assertThat(document.select("#home-converge-title").text()).isEqualTo("세계의 랜드마크");
                    assertThat(document.select(".home-converge-eyebrow").text()).isEqualTo("Landmarks");

                    // 있는 만큼만 그린다. 스크롤 효과가 옮기는 li 안에 링크가 있다.
                    assertThat(document.select("[data-home-converge] > li.home-converge-card")).hasSize(3);
                    // 순환 복제본이 앞뒤에 붙어도 원본 첫·마지막 카드를 알 수 있게 서버가 표시한다.
                    assertThat(document.select(".home-converge-card.is-first h3").text()).isEqualTo("빅벤");
                    assertThat(document.select(".home-converge-card.is-last h3").text()).isEqualTo("성산일출봉");
                    assertThat(document.select(".home-converge-card.is-clone")).isEmpty();
                    assertThat(document.select(".home-converge-card > a.home-converge-link").eachAttr("href"))
                            .containsExactly("/destinations/31", "/destinations/15", "/destinations/40");
                    assertThat(document.select(".home-converge-card h3").eachText())
                            .containsExactly("빅벤", "경복궁", "성산일출봉");
                    assertThat(document.select(".home-converge-card p").eachText())
                            .containsExactly("영국 런던", "서울 종로구", "제주");

                    var images = document.select(".home-converge-photo img");
                    assertThat(images.get(0).attr("src")).isEqualTo("https://upload.wikimedia.org/big-ben.jpg");
                    assertThat(images.get(0).hasAttr("srcset")).isFalse();
                    assertThat(images.get(1).attr("src")).isEqualTo("/destination-thumbnails/v1/480/palace.jpg");
                    assertThat(images.get(1).attr("srcset")).contains("960w");
                    assertThat(images.get(1).attr("sizes")).contains("calc(170px * 1.78)");
                    assertThat(images.get(1).attr("data-original-src")).isEqualTo("/uploads/destinations/palace.jpg");
                    assertThat(images.get(2).attr("src")).isEqualTo("/uploads/destinations/seongsan.jpg");

                    // 수동 레일의 이전/다음 버튼: button + aria-label, 넘길 카드가 있을 때만 JS 가 보여 준다.
                    var rail = document.selectFirst(".home-converge-rail");
                    assertThat(rail.selectFirst("#home-converge-list[data-home-converge]")).isNotNull();
                    var navButtons = rail.select("button.home-converge-nav");
                    assertThat(navButtons.eachAttr("aria-label"))
                            .containsExactly("이전 랜드마크", "다음 랜드마크");
                    assertThat(navButtons.eachAttr("aria-controls"))
                            .containsExactly("home-converge-list", "home-converge-list");
                    assertThat(navButtons).allMatch(button -> button.hasAttr("hidden")
                            && "button".equals(button.attr("type")));
                });

        verify(cardThumbnailService).applyCardImages(eq(landmarks), any(), any());
    }

    private SeasonDestinationDto landmark(Long id, String name, String imageUrl,
                                          String parentRegionName, String regionName) {
        SeasonDestinationDto destination = new SeasonDestinationDto();
        destination.setId(id);
        destination.setName(name);
        destination.setImageUrl(imageUrl);
        destination.setParentRegionName(parentRegionName);
        destination.setRegionName(regionName);
        return destination;
    }

    @Test
    void withdrawalQueryRendersAnAccessibleToastWithoutAddingAHomeLayoutBanner()
            throws Exception {
        when(courseService.getPopularCoursesForHome(SupportedLanguage.KOREAN))
                .thenReturn(List.of());

        mockMvc.perform(get("/").queryParam("withdrawn", "true"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(
                            ".home-withdrawal-toast[role=status][aria-live=polite]"))
                            .hasSize(1);
                    assertThat(document.select(".home-withdrawal-toast").text())
                            .isEqualTo("회원탈퇴 신청이 완료되었습니다. 계정은 30일 동안 보존됩니다.");
                    assertThat(document.select(".home-account-status")).isEmpty();
                    assertThat(document.select(
                            "script[src='/js/home-withdrawal-toast.js']")).hasSize(1);
                });
    }

    /** 탈퇴 완료 안내는 5개 언어 모두 messages 번들에서 나온다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 회원탈퇴 신청이 완료되었습니다. 계정은 30일 동안 보존됩니다.",
            "en    | Your account deletion request has been received. Your account is kept for 30 days.",
            "ja    | 退会申請が完了しました。アカウントは30日間保存されます。",
            "zh-CN | 注销申请已提交，账号将保留 30 天。",
            "zh-TW | 註銷申請已送出，帳號將保留 30 天。"
    })
    void withdrawalToastFollowsTheCurrentLanguage(String languageTag, String expected)
            throws Exception {
        when(courseService.getPopularCoursesForHome(any())).thenReturn(List.of());

        mockMvc.perform(get("/")
                        .queryParam("withdrawn", "true")
                        .cookie(new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, languageTag)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    assertThat(Jsoup.parse(html).select(".home-withdrawal-toast").text())
                            .isEqualTo(expected);
                    assertThat(html).doesNotContain("??");
                });
    }

    /** withdrawn=true 가 아니면 어떤 언어에서도 안내가 뜨지 않는다. */
    @ParameterizedTest
    @CsvSource({"false", "TRUE", "1", "yes"})
    void onlyTheExactWithdrawnTrueValueRendersTheToast(String value) throws Exception {
        when(courseService.getPopularCoursesForHome(any())).thenReturn(List.of());

        mockMvc.perform(get("/").queryParam("withdrawn", value))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(Jsoup.parse(
                        result.getResponse().getContentAsString())
                        .select(".home-withdrawal-toast")).isEmpty());
    }

    @Test
    void regularHomeDoesNotRenderTheWithdrawalToast() throws Exception {
        when(courseService.getPopularCoursesForHome(SupportedLanguage.KOREAN))
                .thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(Jsoup.parse(
                        result.getResponse().getContentAsString())
                        .select(".home-withdrawal-toast")).isEmpty());
    }

    @Test
    void homeRendersCanonicalSeoHeadAndOnePageHeading() throws Exception {
        when(courseService.getPopularCoursesForHome(SupportedLanguage.KOREAN))
                .thenReturn(List.of());

        mockMvc.perform(get("/").queryParam("withdrawn", "true"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.title()).isEqualTo("TripBora(트립보라) | 여행을 보라, 추억을 남겨라");
                    assertThat(document.selectFirst("meta[name=description]").attr("content"))
                            .contains("TripBora", "트립보라", "여행을 보라, 추억을 남겨라");
                    assertThat(document.selectFirst("link[rel=canonical]").attr("href"))
                            .isEqualTo("https://tripbora.com/");
                    assertThat(document.selectFirst("meta[name=robots]").attr("content"))
                            .isEqualTo("index, follow");
                    assertThat(document.selectFirst("meta[property=og:title]").attr("content"))
                            .isEqualTo(document.title());
                    assertThat(document.selectFirst("meta[property=og:url]").attr("content"))
                            .isEqualTo("https://tripbora.com/");
                    assertThat(document.selectFirst("meta[property=og:site_name]").attr("content"))
                            .isEqualTo("TripBora");
                    assertThat(document.selectFirst("meta[property=og:description]").attr("content"))
                            .contains("여행을 보라, 추억을 남겨라");
                    assertThat(document.selectFirst("meta[name=twitter:card]").attr("content"))
                            .isEqualTo("summary_large_image");
                    assertThat(document.selectFirst("meta[name=twitter:title]").attr("content"))
                            .isEqualTo(document.title());
                    assertThat(document.selectFirst("meta[name=twitter:image]").attr("content"))
                            .isEqualTo("https://tripbora.com/images/branding/tripbora-og.png");
                    assertThat(document.select("link[rel=alternate][hreflang]")).isEmpty();
                    assertThat(document.select(".home-page > h1")).hasSize(1);
                    assertThat(document.select(".home-service-teaser h2").text())
                            .isEqualTo("여행을 보라, 추억을 남겨라");
                    assertThat(document.select(".footer-description").text())
                            .isEqualTo("여행을 보라, 추억을 남겨라");
                    var website = new ObjectMapper().readTree(document
                            .selectFirst("script[type=application/ld+json]").data());
                    assertThat(website.path("@type").asText()).isEqualTo("WebSite");
                    assertThat(website.path("name").asText()).isEqualTo("TripBora");
                    assertThat(website.path("alternateName").asText()).isEqualTo("트립보라");
                    assertThat(website.path("url").asText()).isEqualTo("https://tripbora.com/");
                });
    }

    @Test
    void guestAboutIsPublicAndRendersIndexedSeoMetadata() throws Exception {
        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(view().name("about"))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.title()).isEqualTo("TripBora(트립보라) 소개 | TripBora");
                    assertThat(document.selectFirst("meta[name=description]").attr("content"))
                            .contains("여행지", "여행 계획", "여행 기록");
                    assertThat(document.selectFirst("link[rel=canonical]").attr("href"))
                            .isEqualTo("https://tripbora.com/about");
                    assertThat(document.selectFirst("meta[name=robots]").attr("content"))
                            .isEqualTo("index, follow");
                    assertThat(document.selectFirst("meta[property=og:url]").attr("content"))
                            .isEqualTo("https://tripbora.com/about");
                    assertThat(document.select("main .about-page h1")).hasSize(1);
                    assertThat(document.select(".about-guide-step")).hasSize(5);
                    assertThat(document.select(".about-guide-step a").eachAttr("href"))
                            .containsExactly(
                                    "/destinations", "/travel-info", "/board/list?boardType=course",
                                    "/travel-plans", "/diaries/demo");
                });
    }

    /**
     * 홈은 로그인 여부만 알면 된다. 회원 한 줄을 통째로 읽어 화면으로 넘기지 않는다.
     * (헤더가 쓰는 프로필 사진은 GlobalModelAttributes 가 따로 최소 조회로 넣어 준다)
     */
    @Test
    void authenticatedHomeDoesNotLoadTheWholeMemberRow() throws Exception {
        User user = user(7L);
        when(courseService.getPopularCoursesForHome(SupportedLanguage.KOREAN))
                .thenReturn(List.of());

        mockMvc.perform(get("/").with(authentication(authenticationFor(user))))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attribute("isLoggedIn", true))
                .andExpect(model().attributeDoesNotExist("user"));

        verify(userMapper, never()).findById(7L);
        verify(userMapper, never()).hasLocalPasswordById(7L);
    }

    @Test
    void englishHomeTranslatesFixedUiWhileKeepingDatabaseContentUnchanged() throws Exception {
        HomePopularCourseDto course = new HomePopularCourseDto();
        course.setCourseId(12L);
        course.setTitle("서울 하루 고궁 산책");
        course.setNickname("여행자민준");
        course.setViews(1284);
        course.setTotalDestinationCount(5);
        course.setPreviewDestinationNames(List.of("경복궁", "북촌한옥마을"));
        // 쿠키로 고른 언어가 코스 STOP 이름 조회까지 그대로 전달된다.
        when(courseService.getPopularCoursesForHome(SupportedLanguage.ENGLISH))
                .thenReturn(List.of(course));

        mockMvc.perform(get("/")
                        .cookie(new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, "en")))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".recommend-header").text())
                            .contains("Popular Picks", "Top Destinations at Home and Abroad");
                    assertThat(document.select(".home-section-header").text())
                            .contains("Traveler Stories", "Popular Travel Routes");
                    assertThat(document.select(".popular-course-card").text())
                            .contains("서울 하루 고궁 산책")
                            .contains("여행자민준")
                            .contains("경복궁")
                            .contains("1,284 views")
                            .contains("5 places");
                    assertThat(document.selectFirst("#home-i18n").attr("data-spring-title"))
                            .isEqualTo("Spring: Great Places to Go Now");
                    assertThat(document.selectFirst("#home-i18n").attr("data-event-details"))
                            .isEqualTo("View details");
                });
    }

    private UsernamePasswordAuthenticationToken authenticationFor(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(
                userDetails, userDetails.getPassword(), userDetails.getAuthorities());
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUserPassword("encoded-password");
        user.setUserRole(UserRole.USER);
        return user;
    }
}
