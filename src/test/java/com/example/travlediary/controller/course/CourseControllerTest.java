package com.example.travlediary.controller.course;

import com.example.travlediary.dto.CourseCreateRequest;
import com.example.travlediary.dto.CourseEditDto;
import com.example.travlediary.dto.CourseUpdateRequest;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.course.CourseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(courseService.getCourseForEdit(100L, 5L)).thenReturn(course);
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(korea, japan));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.courseEditPage(100L, userDetails, model);

        assertThat(view).isEqualTo("course/edit");
        assertThat(model.get("course")).isSameAs(course);
        assertThat(model.get("domesticCourseCountries")).isEqualTo(List.of(korea));
        assertThat(model.get("overseasCourseCountries")).isEqualTo(List.of(japan));
    }

    private CountryCategory country(Long id, String name, Long parentId) {
        CountryCategory country = new CountryCategory();
        country.setId(id);
        country.setRegionName(name);
        country.setParentId(parentId);
        return country;
    }
}
