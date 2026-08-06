package com.example.travlediary.controller.course;

import com.example.travlediary.dto.CourseCreateRequest;
import com.example.travlediary.dto.CourseUpdateRequest;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.course.CourseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseControllerTest {

    @Mock
    private CourseService courseService;
    @Mock
    private CustomUserDetails userDetails;

    @Test
    void createUsesAuthenticatedUserAndRedirectsToNewDetail() {
        CourseController controller = new CourseController(courseService);
        CourseCreateRequest request = new CourseCreateRequest();
        when(userDetails.getId()).thenReturn(5L);
        when(courseService.createCourse(request, 5L)).thenReturn(100L);

        String view = controller.submitCourse(request, userDetails);

        verify(courseService).createCourse(request, 5L);
        assertThat(view).isEqualTo("redirect:/course/100");
    }

    @Test
    void updateUsesAuthenticatedUserAndRedirectsToDetail() {
        CourseController controller = new CourseController(courseService);
        CourseUpdateRequest request = new CourseUpdateRequest();
        when(userDetails.getId()).thenReturn(5L);

        String view = controller.updateCourse(100L, request, userDetails);

        verify(courseService).updateCourse(100L, 5L, request);
        assertThat(view).isEqualTo("redirect:/course/100");
    }

    @Test
    void deleteUsesAuthenticatedUserAndRedirectsToUnifiedBoard() {
        CourseController controller = new CourseController(courseService);
        when(userDetails.getId()).thenReturn(5L);

        String view = controller.deleteCourse(100L, userDetails);

        verify(courseService).deleteCourse(100L, 5L);
        assertThat(view).isEqualTo("redirect:/board/list");
    }
}
