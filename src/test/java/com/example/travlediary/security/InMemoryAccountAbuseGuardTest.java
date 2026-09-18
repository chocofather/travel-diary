package com.example.travlediary.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.travlediary.security.AccountAbuseGuard.RecoveryEmailKind.PASSWORD_RESET;
import static com.example.travlediary.security.AccountAbuseGuard.RecoveryEmailKind.USERNAME_RECOVERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 회원 관련 공개 endpoint 의 요청 한도 계약.
 *
 * <p>시간은 흐르게 두지 않고 {@link Clock} 을 직접 옮긴다. 창이 지나면 다시 열리는지,
 * 다른 IP 가 함께 막히지 않는지처럼 시간에 기대는 것들을 기다리지 않고 확인할 수 있다.
 */
class InMemoryAccountAbuseGuardTest {

    private static final Instant START = Instant.parse("2026-09-18T09:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final InMemoryAccountAbuseGuard guard = new InMemoryAccountAbuseGuard(movableClock());

    /* ===== M3 존재 확인 조회 ===== */

    /** 가입 폼이 실제로 쓰는 만큼의 조회는 그대로 지나간다. */
    @Test
    void anOrdinarySignupFormSessionIsNeverThrottled() {
        assertThatCode(() -> {
            for (int attempt = 0; attempt < InMemoryAccountAbuseGuard.EXISTENCE_LOOKUP_LIMIT;
                 attempt++) {
                guard.checkExistenceLookup("203.0.113.10");
            }
        }).doesNotThrowAnyException();
    }

    /** 한도를 넘으면 막고, 얼마 뒤에 다시 되는지 알려 준다. */
    @Test
    void lookupsBeyondTheLimitAreRejectedWithARetryHint() {
        fillLookupLimit("203.0.113.10");

        assertThatThrownBy(() -> guard.checkExistenceLookup("203.0.113.10"))
                .isInstanceOf(TooManyAccountRequestsException.class)
                .extracting(exception ->
                        ((TooManyAccountRequestsException) exception).getRetryAfterSeconds())
                .satisfies(seconds -> assertThat((Long) seconds)
                        .isBetween(1L, InMemoryAccountAbuseGuard
                                .EXISTENCE_LOOKUP_WINDOW.toSeconds()));
    }

    /** 창이 지나면 다시 열린다. */
    @Test
    void lookupsAreAllowedAgainOnceTheWindowHasPassed() {
        fillLookupLimit("203.0.113.10");
        assertThatThrownBy(() -> guard.checkExistenceLookup("203.0.113.10"))
                .isInstanceOf(TooManyAccountRequestsException.class);

        advance(InMemoryAccountAbuseGuard.EXISTENCE_LOOKUP_WINDOW.plusSeconds(1));

        assertThatCode(() -> guard.checkExistenceLookup("203.0.113.10"))
                .doesNotThrowAnyException();
    }

    /** 한 사람이 막혀도 다른 사람은 그대로 쓴다. */
    @Test
    void oneThrottledClientDoesNotBlockAnother() {
        fillLookupLimit("203.0.113.10");

        assertThatThrownBy(() -> guard.checkExistenceLookup("203.0.113.10"))
                .isInstanceOf(TooManyAccountRequestsException.class);
        assertThatCode(() -> guard.checkExistenceLookup("198.51.100.20"))
                .doesNotThrowAnyException();
    }

    /* ===== M4 메일 요청 (IP 축) ===== */

    /** 주소를 바꿔 가며 불러도 같은 IP 라면 함께 걸린다. */
    @Test
    void recoveryRequestsFromOneClientAreCappedRegardlessOfTheEmailUsed() {
        for (int attempt = 0; attempt < InMemoryAccountAbuseGuard.RECOVERY_REQUEST_LIMIT;
             attempt++) {
            guard.checkRecoveryRequest("203.0.113.10");
        }

        assertThatThrownBy(() -> guard.checkRecoveryRequest("203.0.113.10"))
                .isInstanceOf(TooManyAccountRequestsException.class);
        // 메일 한도는 조회 한도와 따로 센다. 하나가 찼다고 다른 하나가 닫히지 않는다.
        assertThatCode(() -> guard.checkExistenceLookup("203.0.113.10"))
                .doesNotThrowAnyException();
    }

    /** 메일 요청 창이 지나면 다시 열린다. */
    @Test
    void recoveryRequestsAreAllowedAgainOnceTheWindowHasPassed() {
        for (int attempt = 0; attempt < InMemoryAccountAbuseGuard.RECOVERY_REQUEST_LIMIT;
             attempt++) {
            guard.checkRecoveryRequest("203.0.113.10");
        }
        advance(InMemoryAccountAbuseGuard.RECOVERY_REQUEST_WINDOW.plusSeconds(1));

        assertThatCode(() -> guard.checkRecoveryRequest("203.0.113.10"))
                .doesNotThrowAnyException();
    }

    /* ===== M4 메일 재발송 (이메일 축) ===== */

    /** 같은 주소로는 cooldown 안에 한 통만 나간다. */
    @Test
    void theSameAddressOnlyReceivesOneMailPerCooldown() {
        assertThat(guard.allowRecoveryEmail(PASSWORD_RESET, "member@gmail.com")).isTrue();
        assertThat(guard.allowRecoveryEmail(PASSWORD_RESET, "member@gmail.com")).isFalse();

        advance(InMemoryAccountAbuseGuard.RECOVERY_EMAIL_COOLDOWN.plusSeconds(1));

        assertThat(guard.allowRecoveryEmail(PASSWORD_RESET, "member@gmail.com")).isTrue();
    }

    /** 메일 종류가 다르면 서로의 cooldown 에 걸리지 않는다. */
    @Test
    void differentMailKindsKeepTheirOwnCooldown() {
        assertThat(guard.allowRecoveryEmail(PASSWORD_RESET, "member@gmail.com")).isTrue();

        assertThat(guard.allowRecoveryEmail(USERNAME_RECOVERY, "member@gmail.com")).isTrue();
        assertThat(guard.allowRecoveryEmail(USERNAME_RECOVERY, "member@gmail.com")).isFalse();
    }

    /** 다른 주소는 함께 막히지 않는다. */
    @Test
    void oneAddressInCooldownDoesNotBlockAnother() {
        assertThat(guard.allowRecoveryEmail(PASSWORD_RESET, "member@gmail.com")).isTrue();

        assertThat(guard.allowRecoveryEmail(PASSWORD_RESET, "member@gmail.com")).isFalse();
        assertThat(guard.allowRecoveryEmail(PASSWORD_RESET, "other@gmail.com")).isTrue();
    }

    /** 대소문자만 다른 주소는 같은 주소로 본다. */
    @Test
    void addressesThatDifferOnlyInCaseShareOneCooldown() {
        assertThat(guard.allowRecoveryEmail(PASSWORD_RESET, "member@gmail.com")).isTrue();
        assertThat(guard.allowRecoveryEmail(PASSWORD_RESET, "MEMBER@GMAIL.COM")).isFalse();
    }

    /** cooldown 은 예외를 던지지 않는다. 부르는 쪽 응답이 달라지면 계정 존재가 드러난다. */
    @Test
    void theEmailCooldownNeverThrows() {
        guard.allowRecoveryEmail(PASSWORD_RESET, "member@gmail.com");

        assertThatCode(() -> guard.allowRecoveryEmail(PASSWORD_RESET, "member@gmail.com"))
                .doesNotThrowAnyException();
    }

    /* ===== 도우미 ===== */

    private void fillLookupLimit(String ipAddress) {
        for (int attempt = 0; attempt < InMemoryAccountAbuseGuard.EXISTENCE_LOOKUP_LIMIT;
             attempt++) {
            guard.checkExistenceLookup(ipAddress);
        }
    }

    private void advance(Duration amount) {
        now.updateAndGet(current -> current.plus(amount));
    }

    /** {@link #now} 가 가리키는 시각을 그대로 돌려주는 시계. */
    private Clock movableClock() {
        return new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
    }
}
