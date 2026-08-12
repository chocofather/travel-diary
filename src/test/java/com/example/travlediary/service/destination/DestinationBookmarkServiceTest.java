package com.example.travlediary.service.destination;

import com.example.travlediary.repository.bookmark.BookmarkMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DestinationBookmarkServiceTest {

    @Mock
    private BookmarkMapper bookmarkMapper;

    @Test
    void removeIsIdempotentAndUsesTheServerFixedDestinationType() {
        DestinationBookmarkService service = new DestinationBookmarkService(bookmarkMapper);

        service.removeBookmark(7L, 10L);

        verify(bookmarkMapper).delete(7L, "DESTINATION", 10L);
    }
}
