package com.example.travlediary.controller.bookmark;

import com.example.travlediary.controller.destination.DestinationBookmarkController;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.destination.DestinationBookmarkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationBookmarkControllerTest {

    @Mock
    private DestinationBookmarkService service;
    @Mock
    private UserMapper userMapper;
    @Mock
    private CustomUserDetails userDetails;

    @Test
    void explicitDeleteUsesPrincipalIdAndReturnsNoContent() {
        DestinationBookmarkController controller =
                new DestinationBookmarkController(service, userMapper);
        when(userDetails.getId()).thenReturn(7L);

        var response = controller.removeBookmark(10L, userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).removeBookmark(7L, 10L);
    }
}
