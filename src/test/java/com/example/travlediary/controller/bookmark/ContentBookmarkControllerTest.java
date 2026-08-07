package com.example.travlediary.controller.bookmark;

import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.bookmark.ContentBookmarkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentBookmarkControllerTest {

    @Mock
    private ContentBookmarkService service;
    @Mock
    private CustomUserDetails userDetails;

    @Test
    void postBookmarkEndpointsUsePrincipalIdAndReturnNoContent() {
        ContentBookmarkController controller = new ContentBookmarkController(service);
        when(userDetails.getId()).thenReturn(7L);

        var create = controller.bookmarkPost(10L, userDetails);
        var delete = controller.unbookmarkPost(10L, userDetails);

        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).bookmarkPost(10L, 7L);
        verify(service).unbookmarkPost(10L, 7L);
    }

    @Test
    void courseBookmarkEndpointsUsePrincipalIdAndReturnNoContent() {
        ContentBookmarkController controller = new ContentBookmarkController(service);
        when(userDetails.getId()).thenReturn(7L);

        var create = controller.bookmarkCourse(20L, userDetails);
        var delete = controller.unbookmarkCourse(20L, userDetails);

        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).bookmarkCourse(20L, 7L);
        verify(service).unbookmarkCourse(20L, 7L);
    }
}
