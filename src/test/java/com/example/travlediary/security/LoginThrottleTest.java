package com.example.travlediary.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {

    private static final String USERNAME = "traveler";
    private static final String IP_ADDRESS = "203.0.113.10";

    private MutableClock clock;
    private LoginThrottle throttle;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-08T00:00:00Z"));
        throttle = new LoginThrottle(clock, Duration.ofHours(24), 50_000, 20_000);
    }

    @Test
    void firstFourAccountFailuresDoNotBlockLogin() {
        recordAccountFailures(4);

        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isFalse();
    }

    @Test
    void fifthAccountFailureBlocksForTenSeconds() {
        recordAccountFailures(5);

        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isTrue();
        clock.advance(Duration.ofSeconds(9));
        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isTrue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isFalse();
    }

    @Test
    void failureResultReportsTheAccountCountAndAuthoritativeBlockDeadline() {
        LoginThrottleStatus status = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            status = throttle.recordFailure(USERNAME, IP_ADDRESS);
        }

        assertThat(status.accountFailureCount()).isEqualTo(4);
        assertThat(status.blockedUntil()).isNull();

        status = throttle.recordFailure(USERNAME, IP_ADDRESS);

        assertThat(status.accountFailureCount()).isEqualTo(5);
        assertThat(status.blockedUntil())
                .isEqualTo(Instant.parse("2026-09-08T00:00:10Z"));
        assertThat(throttle.status(USERNAME, IP_ADDRESS)).isEqualTo(status);
    }

    @Test
    void sixthAccountFailureBlocksForThirtySeconds() {
        recordAccountFailures(5);
        clock.advance(Duration.ofSeconds(10));
        throttle.recordFailure(USERNAME, IP_ADDRESS);

        clock.advance(Duration.ofSeconds(29));
        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isTrue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isFalse();
    }

    @Test
    void seventhAccountFailureBlocksForOneMinute() {
        reachSixFailuresAndWait();
        throttle.recordFailure(USERNAME, IP_ADDRESS);

        clock.advance(Duration.ofSeconds(59));
        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isTrue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isFalse();
    }

    @Test
    void eighthAndLaterAccountFailuresBlockForFiveMinutes() {
        reachSevenFailuresAndWait();
        throttle.recordFailure(USERNAME, IP_ADDRESS);

        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isTrue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isFalse();

        throttle.recordFailure(USERNAME, IP_ADDRESS);
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isTrue();
    }

    @Test
    void successfulLoginClearsOnlyTheAccountFailureHistory() {
        recordAccountFailures(5);

        throttle.recordSuccess(" TRAVELER ");

        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isFalse();
        recordAccountFailures(4);
        assertThat(throttle.isBlocked(USERNAME, "198.51.100.20")).isFalse();
    }

    @Test
    void twentyFailuresAcrossDifferentAccountsBlockTheIpForFiveMinutes() {
        for (int attempt = 1; attempt <= 19; attempt++) {
            throttle.recordFailure("member" + attempt, IP_ADDRESS);
        }
        assertThat(throttle.isBlocked("another-member", IP_ADDRESS)).isFalse();

        throttle.recordFailure("member20", IP_ADDRESS);

        assertThat(throttle.isBlocked("another-member", IP_ADDRESS)).isTrue();
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(throttle.isBlocked("another-member", IP_ADDRESS)).isTrue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(throttle.isBlocked("another-member", IP_ADDRESS)).isFalse();
    }

    @Test
    void ipOnlyStatusDoesNotApplyTheSharedEmptyAccountBlockToAnotherIp() {
        for (int attempt = 0; attempt < 5; attempt++) {
            throttle.recordFailure("", "198.51.100.10");
        }

        assertThat(throttle.ipStatus("198.51.100.11").blocked()).isFalse();
    }

    @Test
    void failuresAcrossTheMinuteBoundaryStillCountInTheRollingWindow() {
        throttle.recordFailure("window-anchor", IP_ADDRESS);
        clock.advance(Duration.ofSeconds(59));
        for (int attempt = 1; attempt <= 18; attempt++) {
            throttle.recordFailure("before-boundary-" + attempt, IP_ADDRESS);
        }
        assertThat(throttle.isBlocked("another-member", IP_ADDRESS)).isFalse();

        clock.advance(Duration.ofSeconds(2));
        for (int attempt = 1; attempt <= 19; attempt++) {
            throttle.recordFailure("after-boundary-" + attempt, IP_ADDRESS);
        }

        assertThat(throttle.isBlocked("another-member", IP_ADDRESS)).isTrue();
    }

    @Test
    void accountStoreEvictsTheLeastRecentlyUsedEntryAtItsLimit() {
        LoginThrottle smallThrottle =
                new LoginThrottle(clock, Duration.ofHours(24), 2, 2);
        for (int attempt = 0; attempt < 4; attempt++) {
            smallThrottle.recordFailure("old-account", "192.0.2.1");
        }
        smallThrottle.recordFailure("second-account", "192.0.2.2");
        smallThrottle.recordFailure("third-account", "192.0.2.3");

        for (int attempt = 0; attempt < 4; attempt++) {
            smallThrottle.recordFailure("old-account", "192.0.2.4");
        }

        assertThat(smallThrottle.isBlocked("old-account", "192.0.2.5")).isFalse();
    }

    private void recordAccountFailures(int count) {
        for (int attempt = 0; attempt < count; attempt++) {
            throttle.recordFailure(USERNAME, IP_ADDRESS);
        }
    }

    private void reachSixFailuresAndWait() {
        recordAccountFailures(5);
        clock.advance(Duration.ofSeconds(10));
        throttle.recordFailure(USERNAME, IP_ADDRESS);
        clock.advance(Duration.ofSeconds(30));
    }

    private void reachSevenFailuresAndWait() {
        reachSixFailuresAndWait();
        throttle.recordFailure(USERNAME, IP_ADDRESS);
        clock.advance(Duration.ofMinutes(1));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
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
            return instant;
        }
    }
}
