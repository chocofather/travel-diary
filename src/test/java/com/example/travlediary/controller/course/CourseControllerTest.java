package com.example.travlediary.controller.course;

import com.example.travlediary.dto.CourseCreateRequest;
import com.example.travlediary.dto.CourseDetailDto;
import com.example.travlediary.dto.CourseEditDto;
import com.example.travlediary.dto.CourseStopDto;
import com.example.travlediary.dto.CourseUpdateRequest;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.course.CourseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseControllerTest {

    @Mock
    private CourseService courseService;
    @Mock
    private CountryCategoryService countryCategoryService;
    @Mock
    private CustomUserDetails userDetails;

    @Test
    void createUsesAuthenticatedUserAndRedirectsToNewDetail() {
        CourseController controller = new CourseController(courseService, countryCategoryService);
        CourseCreateRequest request = new CourseCreateRequest();
        when(userDetails.getId()).thenReturn(5L);
        when(courseService.createCourse(request, 5L)).thenReturn(100L);

        String view = controller.submitCourse(request, userDetails);

        verify(courseService).createCourse(request, 5L);
        assertThat(view).isEqualTo("redirect:/course/100");
    }

    @Test
    void updateUsesAuthenticatedUserAndRedirectsToDetail() {
        CourseController controller = new CourseController(courseService, countryCategoryService);
        CourseUpdateRequest request = new CourseUpdateRequest();
        when(userDetails.getId()).thenReturn(5L);

        String view = controller.updateCourse(100L, request, userDetails);

        verify(courseService).updateCourse(100L, 5L, request);
        assertThat(view).isEqualTo("redirect:/course/100");
    }

    @Test
    void deleteUsesAuthenticatedUserAndRedirectsToUnifiedBoard() {
        CourseController controller = new CourseController(courseService, countryCategoryService);
        when(userDetails.getId()).thenReturn(5L);

        String view = controller.deleteCourse(100L, userDetails);

        verify(courseService).deleteCourse(100L, 5L);
        assertThat(view).isEqualTo("redirect:/board/list");
    }

    @Test
    void writePageSeparatesRootAndOverseasCountriesFromServerData() {
        CourseController controller = new CourseController(courseService, countryCategoryService);
        CountryCategory korea = country(7L, "대한민국", null);
        CountryCategory japan = country(8L, "일본", 1L);
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(korea, japan));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.courseWritePage(model);

        assertThat(view).isEqualTo("course/write");
        assertThat(model.get("domesticCourseCountries")).isEqualTo(List.of(korea));
        assertThat(model.get("overseasCourseCountries")).isEqualTo(List.of(japan));
    }

    @Test
    void editPageLoadsExistingCourseAndTheSameCountryOptionsAsCreate() {
        CourseController controller = new CourseController(courseService, countryCategoryService);
        CourseEditDto course = new CourseEditDto();
        course.setId(100L);
        course.setCountryId(8L);
        course.setCountryName("일본");
        CountryCategory korea = country(7L, "대한민국", null);
        CountryCategory japan = country(8L, "일본", 1L);
        when(userDetails.getId()).thenReturn(5L);
        when(courseService.getCourseForEdit(eq(100L), eq(5L), any())).thenReturn(course);
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(korea, japan));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.courseEditPage(100L, userDetails, model);

        assertThat(view).isEqualTo("course/edit");
        assertThat(model.get("course")).isSameAs(course);
        assertThat(model.get("domesticCourseCountries")).isEqualTo(List.of(korea));
        assertThat(model.get("overseasCourseCountries")).isEqualTo(List.of(japan));
    }

    @Test
    void publicCourseDetailAddsTouristTripJsonLdWithOrderedStops() throws Exception {
        CourseDetailDto course = new CourseDetailDto();
        course.setId(100L);
        course.setTitle("서울 궁궐 산책");
        course.setContent("<p>두 궁궐을 차례로 걷는 코스입니다.</p>");
        course.setStops(List.of(stop(21L, 1, "경복궁"), stop(22L, 2, "창덕궁")));
        when(courseService.getCourseDetail(eq(100L), eq(null), any())).thenReturn(course);
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute("seoSiteBaseUrl", "https://travel.example");
        CourseController controller = new CourseController(courseService, countryCategoryService);

        controller.courseDetail(100L, null, model);

        JsonNode trip = new ObjectMapper().readTree((String) model.get("seoJsonLd"))
                .path("@graph").get(0);
        assertThat(trip.path("@type").asText()).isEqualTo("TouristTrip");
        JsonNode items = trip.path("itinerary").path("itemListElement");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("position").asInt()).isEqualTo(1);
        assertThat(items.get(0).path("item").path("name").asText()).isEqualTo("경복궁");
        assertThat(items.get(0).path("item").path("url").asText())
                .isEqualTo("https://travel.example/destinations/21");
        assertThat(trip.path("url").asText())
                .isEqualTo("https://travel.example/course/100");
        assertThat(trip.has("datePublished")).isFalse();
    }

    private CountryCategory country(Long id, String name, Long parentId) {
        CountryCategory country = new CountryCategory();
        country.setId(id);
        country.setRegionName(name);
        country.setParentId(parentId);
        return country;
    }

    private CourseStopDto stop(Long destinationId, int visitOrder, String name) {
        CourseStopDto stop = new CourseStopDto();
        stop.setDestinationId(destinationId);
        stop.setVisitOrder(visitOrder);
        stop.setName(name);
        return stop;
    }
}
