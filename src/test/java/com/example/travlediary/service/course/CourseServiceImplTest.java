package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCreateRequest;
import com.example.travlediary.dto.CourseDetailDto;
import com.example.travlediary.dto.CourseStopDto;
import com.example.travlediary.dto.CourseUpdateRequest;
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
        detail.setBookmarked(true);
        when(courseMapper.findCourseDetail(10L, 5L)).thenReturn(detail);
        when(courseMapper.findCourseStops(10L)).thenReturn(List.of(stop));

        detail.setUserId(5L);
        CourseDetailDto result = service.getCourseDetail(10L, 5L);

        assertThat(result.getContent())
                .isEqualTo("<p>코스 소개</p>")
                .doesNotContain("script", "onclick");
        assertThat(result.getStops()).containsExactly(stop);
        assertThat(result.isMyCourse()).isTrue();
        assertThat(result.isBookmarked()).isTrue();
        verify(courseMapper).incrementViews(10L);
        verify(courseMapper).findCourseStops(10L);
    }

    @Test
    void returnsEmptyStopListWithoutFailing() {
        CourseDetailDto detail = new CourseDetailDto();
        detail.setContent("<p>소개</p>");
        when(courseMapper.incrementViews(10L)).thenReturn(1);
        when(courseMapper.findCourseDetail(10L, null)).thenReturn(detail);
        when(courseMapper.findCourseStops(10L)).thenReturn(List.of());

        assertThat(service.getCourseDetail(10L, null).getStops()).isEmpty();
    }

    @Test
    void missingOrDeletedCourseReturnsNotFound() {
        when(courseMapper.incrementViews(10L)).thenReturn(0);

        assertThatThrownBy(() -> service.getCourseDetail(10L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(courseMapper, never()).findCourseDetail(10L, null);
        verify(courseMapper, never()).findCourseStops(10L);
    }

    @Test
    void missingDetailAfterIncrementStillReturnsNotFound() {
        when(courseMapper.incrementViews(10L)).thenReturn(1);
        when(courseMapper.findCourseDetail(10L, null)).thenReturn(null);

        assertThatThrownBy(() -> service.getCourseDetail(10L, null))
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

    @Test
    void editReadUsesUnlockedActiveCourseAndOrderedStops() {
        Course course = activeCourse(10L, 5L);
        CourseStopDto first = new CourseStopDto();
        first.setDestinationId(12L);
        CourseStopDto second = new CourseStopDto();
        second.setDestinationId(7L);
        when(courseMapper.findActiveCourse(10L)).thenReturn(course);
        when(courseMapper.findCourseStops(10L)).thenReturn(List.of(first, second));

        var edit = service.getCourseForEdit(10L, 5L);

        assertThat(edit.getStops()).containsExactly(first, second);
        verify(courseMapper, never()).findActiveCourseForUpdate(any());
        verify(courseMapper, never()).incrementViews(any());
    }

    @Test
    void editReadReturnsNotFoundOrForbidden() {
        assertThatThrownBy(() -> service.getCourseForEdit(10L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        when(courseMapper.findActiveCourse(11L)).thenReturn(activeCourse(11L, 9L));
        assertThatThrownBy(() -> service.getCourseForEdit(11L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updatesCourseAndReplacesDestinationsInRequestOrder() {
        CourseUpdateRequest request = updateRequest("  수정 제목  ", "<p>수정 소개</p>", List.of(30L, 12L));
        when(courseMapper.findActiveCourseForUpdate(10L)).thenReturn(activeCourse(10L, 5L));
        when(courseMapper.countExistingDestinations(List.of(30L, 12L))).thenReturn(2);
        when(courseMapper.updateCourse(10L, 5L, "수정 제목", "<p>수정 소개</p>")).thenReturn(1);
        when(courseMapper.deleteCourseDestinations(10L)).thenReturn(0);
        when(courseMapper.insertCourseDestination(any())).thenReturn(1);

        service.updateCourse(10L, 5L, request);

        verify(courseMapper).deleteCourseDestinations(10L);
        ArgumentCaptor<CourseDestination> captor = ArgumentCaptor.forClass(CourseDestination.class);
        verify(courseMapper, org.mockito.Mockito.times(2)).insertCourseDestination(captor.capture());
        assertThat(captor.getAllValues()).extracting(CourseDestination::getDestinationId)
                .containsExactly(30L, 12L);
        assertThat(captor.getAllValues()).extracting(CourseDestination::getVisitOrder)
                .containsExactly(1, 2);
    }

    @Test
    void updateValidatesAllDestinationsBeforeDeletingConnections() {
        CourseUpdateRequest request = updateRequest("제목", "<p>소개</p>", List.of(1L, 1L));
        when(courseMapper.findActiveCourseForUpdate(10L)).thenReturn(activeCourse(10L, 5L));

        assertThatThrownBy(() -> service.updateCourse(10L, 5L, request))
                .isInstanceOf(ResponseStatusException.class);

        verify(courseMapper, never()).updateCourse(any(), any(), any(), any());
        verify(courseMapper, never()).deleteCourseDestinations(any());
    }

    @Test
    void updatePerformsBasicValidationBeforeLocking() {
        CourseUpdateRequest request = updateRequest("제목", "<p>소개</p>", List.of());

        assertThatThrownBy(() -> service.updateCourse(10L, 5L, request))
                .isInstanceOf(ResponseStatusException.class);

        verify(courseMapper, never()).findActiveCourseForUpdate(any());
    }

    @Test
    void updateInsertFailureRaisesRuntimeExceptionForTransactionalRollback() {
        CourseUpdateRequest request = updateRequest("제목", "<p>소개</p>", List.of(1L, 2L));
        when(courseMapper.findActiveCourseForUpdate(10L)).thenReturn(activeCourse(10L, 5L));
        when(courseMapper.countExistingDestinations(List.of(1L, 2L))).thenReturn(2);
        when(courseMapper.updateCourse(10L, 5L, "제목", "<p>소개</p>")).thenReturn(1);
        when(courseMapper.insertCourseDestination(any())).thenReturn(1, 0);

        assertThatThrownBy(() -> service.updateCourse(10L, 5L, request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void softDeleteLocksOwnedCourseAndDoesNotDeleteDestinations() {
        when(courseMapper.findActiveCourseForUpdate(10L)).thenReturn(activeCourse(10L, 5L));
        when(courseMapper.softDeleteCourse(10L, 5L)).thenReturn(1);

        service.deleteCourse(10L, 5L);

        verify(courseMapper).softDeleteCourse(10L, 5L);
        verify(courseMapper, never()).deleteCourseDestinations(any());
    }

    private Course activeCourse(Long id, Long userId) {
        Course course = new Course();
        course.setId(id);
        course.setUserId(userId);
        course.setTitle("기존 제목");
        course.setContent("<p>기존 소개</p>");
        return course;
    }

    private CourseUpdateRequest updateRequest(String title, String content, List<Long> destinationIds) {
        CourseUpdateRequest request = new CourseUpdateRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setDestinationIds(destinationIds);
        return request;
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
