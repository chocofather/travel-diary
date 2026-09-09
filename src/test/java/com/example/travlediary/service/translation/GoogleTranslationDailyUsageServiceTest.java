package com.example.travlediary.service.translation;

import com.example.travlediary.repository.translation.GoogleTranslationDailyUsageMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTranslationDailyUsageServiceTest {

    @Test
    void authenticatedUsageIsReservedByUserIdAndCountsUnicodeCodePoints() {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();
        LocalDate date = LocalDate.of(2026, 9, 9);
        mapper.putUser(date, 42L, 19_995L, 7L);

        service(mapper, "2026-09-09T12:00:00Z").reserve("😀abcd", "203.0.113.9", 42L);

        assertThat(mapper.user(date, 42L).usedCharacters).isEqualTo(20_000L);
        assertThat(mapper.user(date, 42L).providerCalls).isEqualTo(8L);
        assertThat(mapper.ipValues).isEmpty();
    }

    @Test
    void authenticatedRequestOverTheLimitIsRejectedWithoutChangingUsage() {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();
        LocalDate date = LocalDate.of(2026, 9, 9);
        mapper.putUser(date, 42L, 19_999L, 3L);

        assertThatThrownBy(() -> service(mapper, "2026-09-09T12:00:00Z")
                .reserve("ab", "203.0.113.9", 42L))
                .isInstanceOf(TranslationDailyLimitException.class);

        assertThat(mapper.user(date, 42L).usedCharacters).isEqualTo(19_999L);
        assertThat(mapper.user(date, 42L).providerCalls).isEqualTo(3L);
    }

    @Test
    void anonymousUsageIsReservedByHmacIpHash() {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();
        LocalDate date = LocalDate.of(2026, 9, 9);
        TranslationIpSubjectHasher hasher = new TranslationIpSubjectHasher("test-only-secret");

        service(mapper, hasher, "2026-09-09T12:00:00Z", ZoneOffset.UTC)
                .reserve("abc", "203.0.113.9", null);

        byte[] expectedHash = hasher.hash("203.0.113.9");
        assertThat(expectedHash).hasSize(32);
        assertThat(mapper.ip(date, expectedHash).usedCharacters).isEqualTo(3L);
        assertThat(mapper.ip(date, expectedHash).providerCalls).isEqualTo(1L);
        assertThat(mapper.userValues).isEmpty();
    }

    @Test
    void anonymousRequestAtTheLimitIsAllowedAndTheNextOneIsRejected() {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();
        TranslationIpSubjectHasher hasher = new TranslationIpSubjectHasher("test-only-secret");
        GoogleTranslationDailyUsageService service = service(
                mapper, hasher, "2026-09-09T12:00:00Z", ZoneOffset.UTC);

        service.reserve("a".repeat(5_000), "203.0.113.9", null);
        assertThatThrownBy(() -> service.reserve("b", "203.0.113.9", null))
                .isInstanceOf(TranslationDailyLimitException.class);

        assertThat(mapper.ip(LocalDate.of(2026, 9, 9), hasher.hash("203.0.113.9")).usedCharacters)
                .isEqualTo(5_000L);
    }

    @Test
    void utcDateChangesAtUtcMidnight() {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();

        service(mapper, "2026-09-09T23:59:59Z").reserve("abc", "unused", 42L);
        service(mapper, "2026-09-10T00:00:00Z").reserve("de", "unused", 42L);

        assertThat(mapper.user(LocalDate.of(2026, 9, 9), 42L).usedCharacters).isEqualTo(3L);
        assertThat(mapper.user(LocalDate.of(2026, 9, 10), 42L).usedCharacters).isEqualTo(2L);
    }

    @Test
    void serverClockZoneDoesNotChangeTheUtcUsageDate() {
        Instant instant = Instant.parse("2026-09-09T15:30:00Z");
        InMemoryUsageMapper utcMapper = new InMemoryUsageMapper();
        InMemoryUsageMapper seoulMapper = new InMemoryUsageMapper();

        service(utcMapper, new TranslationIpSubjectHasher("test-only-secret"), instant, ZoneOffset.UTC)
                .reserve("a", "unused", 42L);
        service(seoulMapper, new TranslationIpSubjectHasher("test-only-secret"),
                instant, ZoneId.of("Asia/Seoul")).reserve("a", "unused", 42L);

        assertThat(utcMapper.userValues.keySet()).containsExactly(new UserKey(LocalDate.of(2026, 9, 9), 42L));
        assertThat(seoulMapper.userValues.keySet()).containsExactly(new UserKey(LocalDate.of(2026, 9, 9), 42L));
    }

    @Test
    void concurrentReservationsNeverExceedTheDailyLimit() throws Exception {
        InMemoryUsageMapper mapper = new InMemoryUsageMapper();
        GoogleTranslationDailyUsageService service = service(mapper, "2026-09-09T12:00:00Z");
        String request = "a".repeat(15_000);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> reserve(service, request));
            var second = executor.submit(() -> reserve(service, request));

            int accepted = (first.get(2, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(2, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(accepted).isEqualTo(1);
            assertThat(mapper.user(LocalDate.of(2026, 9, 9), 42L).usedCharacters).isEqualTo(15_000L);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean reserve(GoogleTranslationDailyUsageService service, String sourceText) {
        try {
            service.reserve(sourceText, "unused", 42L);
            return true;
        } catch (TranslationDailyLimitException e) {
            return false;
        }
    }

    private GoogleTranslationDailyUsageService service(InMemoryUsageMapper mapper, String instant) {
        return service(mapper, new TranslationIpSubjectHasher("test-only-secret"),
                Instant.parse(instant), ZoneOffset.UTC);
    }

    private GoogleTranslationDailyUsageService service(InMemoryUsageMapper mapper,
                                                        TranslationIpSubjectHasher hasher,
                                                        String instant,
                                                        ZoneId zone) {
        return service(mapper, hasher, Instant.parse(instant), zone);
    }

    private GoogleTranslationDailyUsageService service(InMemoryUsageMapper mapper,
                                                        TranslationIpSubjectHasher hasher,
                                                        Instant instant,
                                                        ZoneId zone) {
        return new GoogleTranslationDailyUsageService(
                mapper, properties(), hasher, Clock.fixed(instant, zone));
    }

    private TranslationProperties properties() {
        return new TranslationProperties(
                2000, Duration.ofSeconds(30), Duration.ofSeconds(30),
                100, 100, 100, Duration.ofMinutes(1), true, 400_000L,
                20_000L, 5_000L);
    }

    private record UserKey(LocalDate usageDate, Long userId) {
    }

    private record IpKey(LocalDate usageDate, byte[] ipHash) {
        @Override
        public boolean equals(Object other) {
            return other instanceof IpKey key
                    && usageDate.equals(key.usageDate)
                    && Arrays.equals(ipHash, key.ipHash);
        }

        @Override
        public int hashCode() {
            return 31 * usageDate.hashCode() + Arrays.hashCode(ipHash);
        }
    }

    private static final class InMemoryUsageMapper implements GoogleTranslationDailyUsageMapper {
        private final Map<UserKey, Usage> userValues = new ConcurrentHashMap<>();
        private final Map<IpKey, Usage> ipValues = new ConcurrentHashMap<>();

        @Override
        public void insertUserDayIfAbsent(LocalDate usageDate, Long userId, Timestamp now) {
            userValues.putIfAbsent(new UserKey(usageDate, userId), new Usage());
        }

        @Override
        public synchronized int reserveUserIfWithinLimit(LocalDate usageDate, Long userId,
                                                         long characters, long characterLimit,
                                                         Timestamp now) {
            Usage usage = userValues.get(new UserKey(usageDate, userId));
            if (characters > characterLimit || usage.usedCharacters > characterLimit - characters) return 0;
            usage.usedCharacters += characters;
            usage.providerCalls++;
            return 1;
        }

        @Override
        public void insertIpDayIfAbsent(LocalDate usageDate, byte[] ipHash, Timestamp now) {
            ipValues.putIfAbsent(new IpKey(usageDate, ipHash.clone()), new Usage());
        }

        @Override
        public synchronized int reserveIpIfWithinLimit(LocalDate usageDate, byte[] ipHash,
                                                       long characters, long characterLimit,
                                                       Timestamp now) {
            Usage usage = ipValues.get(new IpKey(usageDate, ipHash));
            if (characters > characterLimit || usage.usedCharacters > characterLimit - characters) return 0;
            usage.usedCharacters += characters;
            usage.providerCalls++;
            return 1;
        }

        void putUser(LocalDate usageDate, Long userId, long usedCharacters, long providerCalls) {
            userValues.put(new UserKey(usageDate, userId), new Usage(usedCharacters, providerCalls));
        }

        Usage user(LocalDate usageDate, Long userId) {
            return userValues.get(new UserKey(usageDate, userId));
        }

        Usage ip(LocalDate usageDate, byte[] ipHash) {
            return ipValues.get(new IpKey(usageDate, ipHash));
        }
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
