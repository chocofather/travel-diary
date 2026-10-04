package com.tripbora.controller.bookmark;

import com.tripbora.controller.destination.DestinationBookmarkController;
import com.tripbora.security.CustomUserDetails;
import com.tripbora.service.destination.DestinationBookmarkService;
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
    private CustomUserDetails userDetails;

    @Test
    void explicitDeleteUsesPrincipalIdAndReturnsNoContent() {
        DestinationBookmarkController controller =
                new DestinationBookmarkController(service);
        when(userDetails.getId()).thenReturn(7L);

        var response = controller.removeBookmark(10L, userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).removeBookmark(7L, 10L);
    }
}
