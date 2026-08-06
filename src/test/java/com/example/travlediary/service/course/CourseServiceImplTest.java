package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCreateRequest;
import com.example.travlediary.dto.CourseDetailDto;
import com.example.travlediary.dto.CourseStopDto;
import com.example.travlediary.model.Course;
import com.example.travlediary.model.CourseDestination;
import com.example.travlediary.repository.course.CourseMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseMapper courseMapper;

    private CourseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CourseServiceImpl(courseMapper, new PostContentSanitizer());
    }

    @Test
    void returnsSanitizedDetailAndStopsAfterIncrementingViews() {
        CourseDetailDto detail = new CourseDetailDto();
        detail.setId(10L);
        detail.setContent("<p onclick=\"alert(1)\">코스 소개</p><script>alert(1)</script>");
        CourseStopDto stop = new CourseStopDto();
        stop.setDestinationId(20L);

        when(courseMapper.incrementViews(10L)).thenReturn(1);
        when(courseMapper.findCourseDetail(10L)).thenReturn(detail);
        when(courseMapper.findCourseStops(10L)).thenReturn(List.of(stop));

        CourseDetailDto result = service.getCourseDetail(10L);

        assertThat(result.getContent())
                .isEqualTo("<p>코스 소개</p>")
                .doesNotContain("script", "onclick");
        assertThat(result.getStops()).containsExactly(stop);
        verify(courseMapper).incrementViews(10L);
        verify(courseMapper).findCourseStops(10L);
    }

    @Test
    void returnsEmptyStopListWithoutFailing() {
        CourseDetailDto detail = new CourseDetailDto();
        detail.setContent("<p>소개</p>");
        when(courseMapper.incrementViews(10L)).thenReturn(1);
        when(courseMapper.findCourseDetail(10L)).thenReturn(detail);
        when(courseMapper.findCourseStops(10L)).thenReturn(List.of());

        assertThat(service.getCourseDetail(10L).getStops()).isEmpty();
    }

    @Test
    void missingOrDeletedCourseReturnsNotFound() {
        when(courseMapper.incrementViews(10L)).thenReturn(0);

        assertThatThrownBy(() -> service.getCourseDetail(10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(courseMapper, never()).findCourseDetail(10L);
        verify(courseMapper, never()).findCourseStops(10L);
    }

    @Test
    void missingDetailAfterIncrementStillReturnsNotFound() {
        when(courseMapper.incrementViews(10L)).thenReturn(1);
        when(courseMapper.findCourseDetail(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.getCourseDetail(10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createsCourseAndDestinationsInRequestOrder() {
        CourseCreateRequest request = request("  서울 여행  ", "<p>코스 소개</p>", List.of(12L, 7L, 30L));
        when(courseMapper.countExistingDestinations(List.of(12L, 7L, 30L))).thenReturn(3);
        when(courseMapper.insertCourse(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            assertThat(course.getTitle()).isEqualTo("서울 여행");
            assertThat(course.getContent()).isEqualTo("<p>코스 소개</p>");
            assertThat(course.getUserId()).isEqualTo(5L);
            course.setId(100L);
            return 1;
        });
        when(courseMapper.insertCourseDestination(any(CourseDestination.class))).thenReturn(1);

        Long courseId = service.createCourse(request, 5L);

        assertThat(courseId).isEqualTo(100L);
        ArgumentCaptor<CourseDestination> captor = ArgumentCaptor.forClass(CourseDestination.class);
        verify(courseMapper, org.mockito.Mockito.times(3)).insertCourseDestination(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CourseDestination::getDestinationId)
                .containsExactly(12L, 7L, 30L);
        assertThat(captor.getAllValues())
                .extracting(CourseDestination::getVisitOrder)
                .containsExactly(1, 2, 3);
        assertThat(captor.getAllValues())
                .allSatisfy(destination -> assertThat(destination.getCourseId()).isEqualTo(100L));
    }

    @Test
    void rejectsAllInvalidInputBeforeCourseInsert() {
        assertBadRequest(request(" ", "<p>소개</p>", List.of(1L)));
        assertBadRequest(request("가".repeat(256), "<p>소개</p>", List.of(1L)));
        assertBadRequest(request("제목", "<p><br></p>", List.of(1L)));
        assertBadRequest(request("제목", "<p>소개</p>", null));
        assertBadRequest(request("제목", "<p>소개</p>", List.of()));
        assertBadRequest(request("제목", "<p>소개</p>", java.util.Arrays.asList(1L, null)));
        assertBadRequest(request("제목", "<p>소개</p>", List.of(1L, 1L)));

        verify(courseMapper, never()).insertCourse(any());
    }

    @Test
    void rejectsNonexistentDestinationBeforeCourseInsert() {
        CourseCreateRequest request = request("제목", "<p>소개</p>", List.of(1L, 2L));
        when(courseMapper.countExistingDestinations(List.of(1L, 2L))).thenReturn(1);

        assertBadRequest(request);

        verify(courseMapper, never()).insertCourse(any());
    }

    @Test
    void failsWhenCourseInsertDoesNotReturnOneOrGeneratedIdIsMissing() {
        CourseCreateRequest request = request("제목", "<p>소개</p>", List.of(1L));
        when(courseMapper.countExistingDestinations(List.of(1L))).thenReturn(1);
        when(courseMapper.insertCourse(any(Course.class))).thenReturn(0);

        assertThatThrownBy(() -> service.createCourse(request, 5L))
                .isInstanceOf(IllegalStateException.class);
        verify(courseMapper, never()).insertCourseDestination(any());
    }

    @Test
    void failsWhenGeneratedCourseIdIsMissing() {
        CourseCreateRequest request = request("제목", "<p>소개</p>", List.of(1L));
        when(courseMapper.countExistingDestinations(List.of(1L))).thenReturn(1);
        when(courseMapper.insertCourse(any(Course.class))).thenReturn(1);

        assertThatThrownBy(() -> service.createCourse(request, 5L))
                .isInstanceOf(IllegalStateException.class);
        verify(courseMapper, never()).insertCourseDestination(any());
    }

    @Test
    void destinationInsertFailureRaisesRuntimeExceptionForRollback() {
        CourseCreateRequest request = request("제목", "<p>소개</p>", List.of(1L, 2L));
        when(courseMapper.countExistingDestinations(List.of(1L, 2L))).thenReturn(2);
        when(courseMapper.insertCourse(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            course.setId(100L);
            return 1;
        });
        when(courseMapper.insertCourseDestination(any(CourseDestination.class)))
                .thenReturn(1)
                .thenReturn(0);

        assertThatThrownBy(() -> service.createCourse(request, 5L))
                .isInstanceOf(IllegalStateException.class);
    }

    private CourseCreateRequest request(String title, String content, List<Long> destinationIds) {
        CourseCreateRequest request = new CourseCreateRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setDestinationIds(destinationIds);
        return request;
    }

    private void assertBadRequest(CourseCreateRequest request) {
        assertThatThrownBy(() -> service.createCourse(request, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
