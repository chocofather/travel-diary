package com.example.travlediary.service.translation;

import com.example.travlediary.repository.translation.GoogleTranslationMonthlyUsageMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTranslationMonthlyUsageServiceTest {

    @Test
    void allowsTheRequestAtTheLimitAndCountsUnicodeCodePoints() {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();
        mapper.put("2026-09", 399_995L, 7L);
        GoogleTranslationMonthlyUsageService service = service(mapper, "2026-09-09T00:00:00Z");

        service.reserve("😀abcd");

        assertThat(mapper.used("2026-09")).isEqualTo(400_000L);
        assertThat(mapper.calls("2026-09")).isEqualTo(8L);
    }

    @Test
    void rejectsARequestThatWouldExceedTheLimitWithoutChangingUsage() {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();
        mapper.put("2026-09", 399_999L, 3L);
        GoogleTranslationMonthlyUsageService service = service(mapper, "2026-09-09T00:00:00Z");

        assertThatThrownBy(() -> service.reserve("ab"))
                .isInstanceOf(TranslationMonthlyLimitException.class);
        assertThat(mapper.used("2026-09")).isEqualTo(399_999L);
        assertThat(mapper.calls("2026-09")).isEqualTo(3L);
    }

    @Test
    void usesANewUsageRowAtThePacificMonthBoundaryDuringDaylightSavingTime() {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();

        service(mapper, "2026-10-01T06:59:59Z").reserve("abc");
        service(mapper, "2026-10-01T07:00:00Z").reserve("de");

        assertThat(mapper.used("2026-09")).isEqualTo(3L);
        assertThat(mapper.used("2026-10")).isEqualTo(2L);
        assertThat(mapper.calls("2026-09")).isEqualTo(1L);
        assertThat(mapper.calls("2026-10")).isEqualTo(1L);
    }

    @Test
    void usesANewUsageRowAtThePacificMonthBoundaryDuringStandardTime() {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();

        service(mapper, "2027-01-01T07:59:59Z").reserve("abc");
        service(mapper, "2027-01-01T08:00:00Z").reserve("de");

        assertThat(mapper.used("2026-12")).isEqualTo(3L);
        assertThat(mapper.used("2027-01")).isEqualTo(2L);
    }

    @Test
    void serverClockZoneDoesNotChangeThePacificMonthKey() {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();
        Instant instant = Instant.parse("2026-10-01T06:30:00Z");

        service(mapper, instant, ZoneOffset.UTC).reserve("a");
        service(mapper, instant, ZoneId.of("Asia/Seoul")).reserve("b");

        assertThat(mapper.used("2026-09")).isEqualTo(2L);
        assertThat(mapper.values).doesNotContainKey("2026-10");
    }

    @Test
    void concurrentReservationsNeverExceedTheMonthlyLimit() throws Exception {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();
        GoogleTranslationMonthlyUsageService service = service(mapper, "2026-09-09T00:00:00Z");
        String request = "a".repeat(300_000);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> reserve(service, request));
            var second = executor.submit(() -> reserve(service, request));

            int accepted = (first.get(2, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(2, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(accepted).isEqualTo(1);
            assertThat(mapper.used("2026-09")).isEqualTo(300_000L);
            assertThat(mapper.calls("2026-09")).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void disabledMonthlyLimitDoesNotAccessTheUsageTable() {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();
        TranslationProperties properties = new TranslationProperties(
                2000, Duration.ofSeconds(30), Duration.ofSeconds(30),
                100, 100, 100, Duration.ofMinutes(1), false, 400_000L,
                20_000L, 5_000L);
        GoogleTranslationMonthlyUsageService service = new GoogleTranslationMonthlyUsageService(
                mapper, properties,
                Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC));

        service.reserve("번역 원문");

        assertThat(mapper.values).isEmpty();
    }

    private boolean reserve(GoogleTranslationMonthlyUsageService service, String sourceText) {
        try {
            service.reserve(sourceText);
            return true;
        } catch (TranslationMonthlyLimitException e) {
            return false;
        }
    }

    private GoogleTranslationMonthlyUsageService service(InMemoryUsageMapper mapper, String instant) {
        return service(mapper, Instant.parse(instant), ZoneOffset.UTC);
    }

    private GoogleTranslationMonthlyUsageService service(InMemoryUsageMapper mapper,
                                                          Instant instant,
                                                          ZoneId clockZone) {
        TranslationProperties properties = new TranslationProperties(
                2000, Duration.ofSeconds(30), Duration.ofSeconds(30),
                100, 100, 100, Duration.ofMinutes(1), true, 400_000L,
                20_000L, 5_000L);
        return new GoogleTranslationMonthlyUsageService(
                mapper, properties, Clock.fixed(instant, clockZone));
    }

    private static final class InMemoryUsageMapper implements GoogleTranslationMonthlyUsageMapper {
        private final Map<String, Usage> values = new ConcurrentHashMap<>();

        @Override
        public void insertMonthIfAbsent(String monthKey, Timestamp now) {
            values.putIfAbsent(monthKey, new Usage());
        }

        @Override
        public synchronized int reserveIfWithinLimit(String monthKey, long characters,
                                                     long characterLimit, Timestamp now) {
            Usage usage = values.get(monthKey);
            if (usage.usedCharacters > characterLimit - characters) return 0;
            usage.usedCharacters += characters;
            usage.providerCalls++;
            return 1;
        }

        void put(String monthKey, long usedCharacters, long providerCalls) {
            values.put(monthKey, new Usage(usedCharacters, providerCalls));
        }

        long used(String monthKey) {
            return values.get(monthKey).usedCharacters;
        }

        long calls(String monthKey) {
            return values.get(monthKey).providerCalls;
        }

        private static final class Usage {
            private long usedCharacters;
            private long providerCalls;

            private Usage() {
            }

            private Usage(long usedCharacters, long providerCalls) {
                this.usedCharacters = usedCharacters;
                this.providerCalls = providerCalls;
            }
        }
    }
}
