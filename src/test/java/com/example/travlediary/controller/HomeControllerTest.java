package com.example.travlediary.controller;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.I18nConfig;
import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.config.i18n.TravelDiaryLocaleResolver;
import com.example.travlediary.dto.HomePopularCourseDto;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.course.CourseService;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
@Import({SecurityConfig.class, I18nConfig.class})
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseService courseService;
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
        when(courseService.getPopularCoursesForHome(SupportedLanguage.KOREAN))
                .thenReturn(List.of(course));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attribute("isLoggedIn", false))
                .andExpect(model().attribute("popularCourses", List.of(course)))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select("#event-slider #slide-area")).hasSize(1);
                    assertThat(document.select(".seasonal-recommend")).hasSize(1);
                    assertThat(document.select(".popular-recommend")).hasSize(1);
                    assertThat(document.select("a.popular-course-card[href='/course/12']")).hasSize(1);
                    assertThat(document.select(".popular-course-card").text())
                            .contains("서울 하루 고궁 산책")
                            .contains("minjun · 조회 1,284")
                            .contains("경복궁 → 북촌한옥마을 → 창덕궁 +2")
                            .contains("장소 5곳");
                    assertThat(document.select(".instant-trip, #roulette-canvas")).isEmpty();
                });

        verify(courseService).getPopularCoursesForHome(SupportedLanguage.KOREAN);
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
    void authenticatedHomeLoadsCurrentUserByPrincipalId() throws Exception {
        User user = user(7L, "member");
        when(userMapper.findById(7L)).thenReturn(user);
        when(courseService.getPopularCoursesForHome(SupportedLanguage.KOREAN))
                .thenReturn(List.of());

        mockMvc.perform(get("/").with(authentication(authenticationFor(user))))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attribute("isLoggedIn", true))
                .andExpect(model().attribute("user", user));

        verify(userMapper, atLeastOnce()).findById(7L);
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
                            .isEqualTo("Spring Destinations Worth Remembering");
                    assertThat(document.selectFirst("#home-i18n").attr("data-event-details"))
                            .isEqualTo("View details");
                });
    }

    private UsernamePasswordAuthenticationToken authenticationFor(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(
                userDetails, userDetails.getPassword(), userDetails.getAuthorities());
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setUserPassword("encoded-password");
        user.setUserRole(UserRole.USER);
        return user;
    }
}
