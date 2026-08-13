package com.example.travlediary.controller;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.HomePopularCourseDto;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.course.CourseService;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
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
        when(courseService.getPopularCoursesForHome()).thenReturn(List.of(course));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
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

        verify(courseService).getPopularCoursesForHome();
    }
}
