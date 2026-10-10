package com.tripbora.service.pixabay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PixabayImageSearchServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
    private PixabayApiClient apiClient;
    private PixabayImageSearchService service;

    @BeforeEach
    void setUp() {
        apiClient = mock(PixabayApiClient.class);
        when(apiClient.isConfigured()).thenReturn(true);
        service = new PixabayImageSearchService(apiClient, clock);
    }

    /** page p 는 ID (p-1)*50+1 ~ p*50 을 준다. */
    private void respondWithPages(String query, String language, int totalHits) {
        when(apiClient.searchPhotos(anyString(), anyString(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int page = invocation.getArgument(2);
            long first = (page - 1) * 50L + 1;
            long last = Math.min(page * 50L, totalHits);
            List<PixabayHit> hits = LongStream.rangeClosed(first, last).mapToObj(PixabayImageSearchServiceTest::hit).toList();
            return new PixabaySearchPage(totalHits, hits, clock.instant());
        });
    }

    @Test
    void firstSearchShowsThirtyThenEachMoreShowsTwentyUsingFiftyPerApiPage() {
        respondWithPages("Eiffel Tower", "en", 200);

        var first = service.search("Eiffel Tower", 0);
        assertThat(first.hits()).extracting(PixabayHit::id).containsExactlyElementsOf(range(1, 30));
        assertThat(first.nextOffset()).isEqualTo(30);
        verify(apiClient, times(1)).searchPhotos("Eiffel Tower", "en", 1, 50);

        // 첫 '더 보기'는 1페이지에 남아 있던 20장이다. 외부 호출이 없다.
        var second = service.search("Eiffel Tower", 30);
        assertThat(second.hits()).extracting(PixabayHit::id).containsExactlyElementsOf(range(31, 50));
        assertThat(second.nextOffset()).isEqualTo(50);
        verify(apiClient, times(1)).searchPhotos(anyString(), anyString(), anyInt(), anyInt());

        // 다음 '더 보기'에서 2페이지(50장)를 받아 20장을 보이고 나머지는 남겨 둔다.
        var third = service.search("Eiffel Tower", 50);
        assertThat(third.hits()).extracting(PixabayHit::id).containsExactlyElementsOf(range(51, 70));
        verify(apiClient).searchPhotos("Eiffel Tower", "en", 2, 50);
        var fourth = service.search("Eiffel Tower", 70);
        assertThat(fourth.hits()).extracting(PixabayHit::id).containsExactlyElementsOf(range(71, 90));
        verify(apiClient, times(2)).searchPhotos(anyString(), anyString(), anyInt(), anyInt());

        Set<Long> all = new HashSet<>();
        for (var result : List.of(first, second, third, fourth)) {
            result.hits().forEach(hit -> assertThat(all.add(hit.id())).as("중복 사진 %s", hit.id()).isTrue());
        }
    }

    @Test
    void theSameIdOnTwoApiPagesIsShownOnlyOnce() {
        when(apiClient.searchPhotos(anyString(), anyString(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int page = invocation.getArgument(2);
            // 2페이지 첫 장은 1페이지 마지막 사진과 같은 ID다(인기순 순위가 바뀌는 경우).
            List<PixabayHit> hits = page == 1
                    ? LongStream.rangeClosed(1, 50).mapToObj(PixabayImageSearchServiceTest::hit).toList()
                    : LongStream.rangeClosed(50, 99).mapToObj(PixabayImageSearchServiceTest::hit).toList();
            return new PixabaySearchPage(200, hits, clock.instant());
        });

        List<Long> shown = new ArrayList<>();
        Integer offset = 0;
        for (int request = 0; request < 4 && offset != null; request++) {
            var result = service.search("Seoul", offset);
            result.hits().forEach(hit -> shown.add(hit.id()));
            offset = result.nextOffset();
        }
        assertThat(shown).doesNotHaveDuplicates().hasSize(90).startsWith(1L, 2L).contains(50L, 51L, 90L);
    }

    @Test
    void sameQueryPageAndOptionsAreServedFromCacheForTwentyFourHours() {
        respondWithPages("Seoul", "en", 200);

        service.search("Seoul", 0);
        clock.advance(Duration.ofHours(23).plusMinutes(59));
        service.search("  Seoul  ", 0);
        verify(apiClient, times(1)).searchPhotos(anyString(), anyString(), anyInt(), anyInt());

        clock.advance(Duration.ofMinutes(1));
        service.search("Seoul", 0);
        verify(apiClient, times(2)).searchPhotos("Seoul", "en", 1, 50);

        // 다른 검색어는 따로 받는다. 국내 여행지라도 영어 검색어에는 lang=ko 를 붙이지 않고,
        // 한글 검색어(한국어 fallback)만 lang=ko 로 찾는다.
        service.search("Gyeongbokgung Palace Seoul South Korea", 0);
        verify(apiClient).searchPhotos("Gyeongbokgung Palace Seoul South Korea", "en", 1, 50);
        service.search("경복궁 서울", 0);
        verify(apiClient).searchPhotos("경복궁 서울", "ko", 1, 50);
    }

    @Test
    void searchedPhotosCanBeFoundByIdForTwentyFourHoursOnly() {
        respondWithPages("Seoul", "en", 200);
        service.search("Seoul", 0);

        assertThat(service.findSearchedHit(12)).map(PixabayHit::pageUrl)
                .contains("https://pixabay.com/photos/sample-12/");
        // 검색 응답에 없던 ID는 찾을 수 없다(외부 호출도 없다).
        assertThat(service.findSearchedHit(999_999)).isEmpty();

        clock.advance(Duration.ofHours(24));
        assertThat(service.findSearchedHit(12)).isEmpty();
        verify(apiClient, times(1)).searchPhotos(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void lastPageEndsTheResultsWithoutAnotherCall() {
        respondWithPages("Jeju", "en", 35);

        var first = service.search("Jeju", 0);
        assertThat(first.hits()).hasSize(30);
        var rest = service.search("Jeju", 30);
        assertThat(rest.hits()).extracting(PixabayHit::id).containsExactlyElementsOf(range(31, 35));
        assertThat(rest.nextOffset()).isNull();
        verify(apiClient, times(1)).searchPhotos(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void emptyResultsHaveNoNextPage() {
        respondWithPages("nothing", "en", 0);

        var result = service.search("nothing", 0);

        assertThat(result.hits()).isEmpty();
        assertThat(result.nextOffset()).isNull();
    }

    @Test
    void missingKeyOrInvalidInputNeverCallsPixabay() {
        when(apiClient.isConfigured()).thenReturn(false);
        assertThatThrownBy(() -> service.search("Seoul", 0))
                .isInstanceOfSatisfying(PixabayApiException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(PixabayApiException.Reason.NOT_CONFIGURED));

        when(apiClient.isConfigured()).thenReturn(true);
        assertThatThrownBy(() -> service.search("   ", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search("x".repeat(101), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search("Seoul", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search("Seoul", 500)).isInstanceOf(IllegalArgumentException.class);
        verify(apiClient, never()).searchPhotos(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void rateLimitIsNotCachedSoTheNextSearchTriesAgain() {
        when(apiClient.searchPhotos(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(PixabayApiException.rateLimited())
                .thenReturn(new PixabaySearchPage(1, List.of(hit(1)), clock.instant()));

        assertThatThrownBy(() -> service.search("Seoul", 0)).isInstanceOf(PixabayApiException.class);
        assertThat(service.search("Seoul", 0).hits()).hasSize(1);
        verify(apiClient, times(2)).searchPhotos(anyString(), anyString(), anyInt(), anyInt());
    }

    static PixabayHit hit(long id) {
        return new PixabayHit(id, "https://pixabay.com/photos/sample-" + id + "/",
                "https://cdn.pixabay.com/photo/p-" + id + "_150.jpg", "https://pixabay.com/get/w" + id + "_640.jpg",
                "https://pixabay.com/get/l" + id + "_1280.jpg", null, null, 4000, 3000, "user" + id, "tag" + id);
    }

    private static List<Long> range(long first, long last) {
        return LongStream.rangeClosed(first, last).boxed().toList();
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
