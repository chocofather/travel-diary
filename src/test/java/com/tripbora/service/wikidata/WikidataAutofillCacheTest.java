package com.tripbora.service.wikidata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikidataAutofillCacheTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WikidataApiClient api = mock(WikidataApiClient.class);
    private final MutableClock clock = new MutableClock();
    private final WikidataAutofillCache cache = new WikidataAutofillCache(api, clock);

    @Test
    void concurrentRequestsForTheSameQidShareOneExternalCall() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JsonNode entity = mapper.readTree("{\"id\":\"Q243\"}");
        when(api.getAutofillEntities(List.of("Q243"))).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return Map.of("Q243", entity);
        });
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Future<JsonNode>> results = new ArrayList<>();
            results.add(pool.submit(() -> cache.entity("Q243")));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            results.add(pool.submit(() -> cache.entity("Q243")));
            results.add(pool.submit(() -> cache.entities(List.of("Q243")).get("Q243")));
            Thread.sleep(50);
            release.countDown();
            for (Future<JsonNode> result : results) assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(entity);
        } finally {
            pool.shutdownNow();
        }
        verify(api, times(1)).getAutofillEntities(anyList());
    }

    @Test
    void entriesExpireAfterTheTtlAndMissingItemsAreRememberedUntilThen() throws Exception {
        when(api.getAutofillEntities(List.of("Q243"))).thenReturn(Map.of("Q243", mapper.readTree("{}")));
        when(api.getAutofillEntities(List.of("Q404"))).thenReturn(Map.of());

        cache.entity("Q243");
        assertThat(cache.entity("Q404")).isNull();
        clock.advance(WikidataAutofillCache.TTL.minusSeconds(1));
        cache.entity("Q243");
        assertThat(cache.entity("Q404")).isNull();
        verify(api, times(1)).getAutofillEntities(List.of("Q243"));
        verify(api, times(1)).getAutofillEntities(List.of("Q404"));

        clock.advance(Duration.ofSeconds(2));
        cache.entity("Q243");
        verify(api, times(2)).getAutofillEntities(List.of("Q243"));
    }

    @Test
    void failedCallsAreNotCachedAndInvalidQidsNeverReachWikidata() throws Exception {
        when(api.getAutofillEntities(List.of("Q243")))
                .thenThrow(new WikidataApiException("Wikidata 요청이 많습니다. 잠시 후 다시 시도해 주세요."))
                .thenReturn(Map.of("Q243", mapper.readTree("{}")));

        assertThatThrownBy(() -> cache.entity("Q243")).isInstanceOf(WikidataApiException.class);
        assertThat(cache.entity("Q243")).isNotNull();
        assertThatThrownBy(() -> cache.entity("q243|Q1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.entity(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.region("Q1|Q2")).isInstanceOf(IllegalArgumentException.class);
        verify(api, times(2)).getAutofillEntities(anyList());
    }

    @Test
    void oldestEntitiesAreEvictedWhenTheLimitIsExceeded() {
        when(api.getAutofillEntities(anyList())).thenAnswer(invocation ->
                Map.of(invocation.<List<String>>getArgument(0).get(0), mapper.createObjectNode()));

        for (int id = 1; id <= 51; id++) cache.entity("Q" + id);
        cache.entity("Q51");
        cache.entity("Q1");

        verify(api, times(1)).getAutofillEntities(List.of("Q51"));
        verify(api, times(2)).getAutofillEntities(List.of("Q1"));
    }

    @Test
    void regionMergesLabelsWithOnlyTheParentRegionClaimAndSkipsMissingItems() throws Exception {
        when(api.getEntities(List.of("Q90"), false)).thenReturn(Map.of("Q90",
                mapper.readTree("{\"labels\":{\"ko\":{\"value\":\"파리\"}}}")));
        when(api.getClaims("Q90", "P131")).thenReturn(mapper.readTree(
                "{\"claims\":{\"P131\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q13917\"}}}}]}}"));
        when(api.getEntities(List.of("Q404"), false)).thenReturn(Map.of());
        when(api.getClaims("Q404", "P131")).thenThrow(new WikidataApiException("no-such-entity"));

        JsonNode region = cache.region("Q90");

        assertThat(region.path("labels").path("ko").path("value").asText()).isEqualTo("파리");
        assertThat(region.path("claims").path("P131").get(0).path("mainsnak").path("datavalue")
                .path("value").path("id").asText()).isEqualTo("Q13917");
        assertThat(cache.region("Q404")).isNull();
        cache.region("Q90");
        verify(api, times(1)).getClaims("Q90", "P131");
        verify(api, never()).getEntities(List.of("Q90"), true);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-26T00:00:00Z");

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
