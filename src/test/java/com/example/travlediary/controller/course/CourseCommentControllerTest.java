package com.example.travlediary.controller.course;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.dto.CourseCommentRequest;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.course.CourseCommentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseCommentControllerTest {

    @Mock
    private CourseCommentService service;
    @Mock
    private CustomUserDetails userDetails;

    @Test
    void guestGetPassesNullUserId() {
        CourseCommentController controller = new CourseCommentController(service);
        when(service.getComments(10L, null)).thenReturn(List.of());

        assertThat(controller.getComments(10L, null)).isEmpty();
        verify(service).getComments(10L, null);
    }

    @Test
    void guestPagedGetPassesPagingAndSortWithNullUserId() {
        CourseCommentController controller = new CourseCommentController(service);
        PageResult<CourseCommentDto> page = new PageResult<>(List.of(), 6, 1, 5, 9);
        when(service.getCommentsPage(10L, null, 1, 5, "likes")).thenReturn(page);

        assertThat(controller.getCommentsPage(10L, 1, 5, "likes", null)).isSameAs(page);
        verify(service).getCommentsPage(10L, null, 1, 5, "likes");
    }

    @Test
    void authenticatedGetPassesPrincipalUserId() {
        CourseCommentController controller = new CourseCommentController(service);
        when(userDetails.getId()).thenReturn(7L);
        when(service.getComments(10L, 7L)).thenReturn(List.of());

        controller.getComments(10L, userDetails);

        verify(service).getComments(10L, 7L);
    }

    @Test
    void createUsesPrincipalAndReturnsCreated() {
        CourseCommentController controller = new CourseCommentController(service);
        CourseCommentRequest request = request(10L, "댓글");
        CourseCommentDto created = new CourseCommentDto();
        when(userDetails.getId()).thenReturn(7L);
        when(service.create(10L, 7L, "댓글", null)).thenReturn(created);

        var response = controller.create(request, userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(created);
        verify(service).create(10L, 7L, "댓글", null);
    }

    @Test
    void createReplyPassesTargetIdAndPrincipalUserIdWithoutTrustingParentId() {
        CourseCommentController controller = new CourseCommentController(service);
        CourseCommentRequest request = request(10L, "대댓글");
        request.setParentCommentId(20L);
        request.setReplyToCommentId(25L);
        CourseCommentDto created = new CourseCommentDto();
        when(userDetails.getId()).thenReturn(7L);
        when(service.create(10L, 7L, "대댓글", 25L)).thenReturn(created);

        var response = controller.create(request, userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(service).create(10L, 7L, "대댓글", 25L);
    }

    @Test
    void updateUsesPrincipalUserId() {
        CourseCommentController controller = new CourseCommentController(service);
        CourseCommentRequest request = request(null, "수정");
        CourseCommentDto updated = new CourseCommentDto();
        when(userDetails.getId()).thenReturn(7L);
        when(service.update(30L, 7L, "수정")).thenReturn(updated);

        assertThat(controller.update(30L, request, userDetails)).isSameAs(updated);
        verify(service).update(30L, 7L, "수정");
    }

    @Test
    void deleteUsesPrincipalAndReturnsNoContent() {
        CourseCommentController controller = new CourseCommentController(service);
        when(userDetails.getId()).thenReturn(7L);

        var response = controller.delete(30L, userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(30L, 7L);
    }

    @Test
    void likeAndUnlikeUsePrincipalUserIdAndReturnNoContent() {
        CourseCommentController controller = new CourseCommentController(service);
        when(userDetails.getId()).thenReturn(7L);

        var likeResponse = controller.likeComment(30L, userDetails);
        var unlikeResponse = controller.unlikeComment(30L, userDetails);

        assertThat(likeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(unlikeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).likeComment(30L, 7L);
        verify(service).unlikeComment(30L, 7L);
    }

    private CourseCommentRequest request(Long courseId, String content) {
        CourseCommentRequest request = new CourseCommentRequest();
        request.setCourseId(courseId);
        request.setContent(content);
        return request;
    }
}
