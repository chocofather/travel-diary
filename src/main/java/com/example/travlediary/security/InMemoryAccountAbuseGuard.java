package com.example.travlediary.security;

import com.example.travlediary.service.user.EmailPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * 단일 인스턴스 메모리로 동작하는 {@link AccountAbuseGuard}.
 *
 * <p>한도는 세 가지다.
 * <ul>
 *   <li>존재 확인 조회 — IP 당 1분에 {@value #EXISTENCE_LOOKUP_LIMIT}회.
 *       가입 폼의 중복 확인은 300ms debounce 뒤에야 한 번 나가므로, 사람이 한 화면을 채우는
 *       동안 이 수에 닿지 않는다. 훑어보는 자동화만 걸린다.</li>
 *   <li>복구·인증 메일 요청 — IP 당 1시간에 {@value #RECOVERY_REQUEST_LIMIT}회.
 *       주소를 바꿔 가며 SMTP 한도를 소진하는 쪽을 막는 넓은 한도다.</li>
 *   <li>같은 주소로의 재발송 — {@link #RECOVERY_EMAIL_COOLDOWN}.
 *       인증 메일 재전송·계정 복구 메일이 이미 쓰는 60초와 같은 값이라 안내 문구가 어긋나지 않는다.</li>
 * </ul>
 *
 * <p>로그는 한 창에 한 줄만 남긴다. 막힌 요청마다 남기면 로그 자체가 공격 표면이 된다.
 * 이메일은 {@link EmailPolicy#mask(String)} 로 가리고, 비밀번호나 토큰은 담지 않는다.
 */
@Component
public class InMemoryAccountAbuseGuard implements AccountAbuseGuard {

    /** 아이디/이메일/닉네임 중복 확인을 한 IP 가 한 창에서 부를 수 있는 횟수. */
    public static final int EXISTENCE_LOOKUP_LIMIT = 60;
    public static final Duration EXISTENCE_LOOKUP_WINDOW = Duration.ofMinutes(1);

    /** 메일이 나갈 수 있는 요청을 한 IP 가 한 창에서 부를 수 있는 횟수. */
    public static final int RECOVERY_REQUEST_LIMIT = 15;
    public static final Duration RECOVERY_REQUEST_WINDOW = Duration.ofHours(1);

    /**
     * 같은 주소로 같은 종류의 메일을 다시 보내기까지 쉬어 가는 시간.
     * 인증 메일 재전송({@code EmailVerificationService.RESEND_COOLDOWN})과 같은 값이다.
     */
    public static final Duration RECOVERY_EMAIL_COOLDOWN = Duration.ofSeconds(60);

    private static final int MAX_TRACKED_IPS = 20_000;
    private static final int MAX_TRACKED_EMAILS = 20_000;

    private static final Logger log = LoggerFactory.getLogger(InMemoryAccountAbuseGuard.class);

    private final FixedWindowRateLimiter existenceLookups;
    private final FixedWindowRateLimiter recoveryRequests;
    private final FixedWindowRateLimiter recoveryEmails;

    @Autowired
    public InMemoryAccountAbuseGuard() {
        this(Clock.systemUTC());
    }

    InMemoryAccountAbuseGuard(Clock clock) {
        this.existenceLookups = new FixedWindowRateLimiter(clock, MAX_TRACKED_IPS);
        this.recoveryRequests = new FixedWindowRateLimiter(clock, MAX_TRACKED_IPS);
        this.recoveryEmails = new FixedWindowRateLimiter(clock, MAX_TRACKED_EMAILS);
    }

    @Override
    public void checkExistenceLookup(String ipAddress) {
        RateLimitDecision decision = existenceLookups.acquire(
                ipKey(ipAddress), EXISTENCE_LOOKUP_LIMIT, EXISTENCE_LOOKUP_WINDOW);
        if (decision.allowed()) {
            return;
        }
        if (decision.firstRejection()) {
            // 주소는 남기지 않는다. 어떤 한도가 걸렸는지만 알면 추적에는 충분하다.
            log.warn("Account existence lookups throttled for one client: limit={}, windowSeconds={}",
                    EXISTENCE_LOOKUP_LIMIT, EXISTENCE_LOOKUP_WINDOW.toSeconds());
        }
        throw new TooManyAccountRequestsException(decision.retryAfterSeconds());
    }

    @Override
    public void checkRecoveryRequest(String ipAddress) {
        RateLimitDecision decision = recoveryRequests.acquire(
                ipKey(ipAddress), RECOVERY_REQUEST_LIMIT, RECOVERY_REQUEST_WINDOW);
        if (decision.allowed()) {
            return;
        }
        if (decision.firstRejection()) {
            log.warn("Account recovery mail requests throttled for one client: limit={}, windowSeconds={}",
                    RECOVERY_REQUEST_LIMIT, RECOVERY_REQUEST_WINDOW.toSeconds());
        }
        throw new TooManyAccountRequestsException(decision.retryAfterSeconds());
    }

    @Override
    public boolean allowRecoveryEmail(RecoveryEmailKind kind, String normalizedEmail) {
        RateLimitDecision decision = recoveryEmails.acquire(
                emailKey(kind, normalizedEmail), 1, RECOVERY_EMAIL_COOLDOWN);
        if (decision.allowed()) {
            return true;
        }
        if (decision.firstRejection()) {
            log.info("Recovery email skipped by cooldown: kind={}, recipient={}",
                    kind, EmailPolicy.mask(normalizedEmail));
        }
        return false;
    }

    /** 신뢰할 수 있는 proxy 설정 전이므로 전달 헤더는 보지 않는다. (M5 에서 따로 다룬다) */
    private String ipKey(String ipAddress) {
        return "ip:" + (ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress.strip());
    }

    private String emailKey(RecoveryEmailKind kind, String normalizedEmail) {
        String email = normalizedEmail == null ? "" : normalizedEmail.strip().toLowerCase(Locale.ROOT);
        return kind.name() + ":" + email;
    }
}
