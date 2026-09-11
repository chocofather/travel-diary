package com.example.travlediary.service.user;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.AccountRecoveryToken;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.AccountRecoveryTokenMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * 탈퇴 유예(WITHDRAWAL_PENDING) 계정의 복구.
 *
 * <p>링크 유효시간과 계정의 유예기간은 서로 다른 개념이다.
 * 링크는 {@link #RECOVERY_TOKEN_VALIDITY} 동안만 살아 있고,
 * 계정을 되돌릴 수 있는지는 언제나 users.purge_scheduled_at 하나로 판단한다.
 * 복구 메일을 다시 요청해도 purge_scheduled_at 은 절대 연장하지 않는다.
 */
@Service
public class AccountRecoveryService {

    /** 복구 링크 기본 유효시간. 메일 안내 문구도 이 값에서 계산한 값을 그대로 쓴다. */
    public static final Duration RECOVERY_TOKEN_VALIDITY = Duration.ofMinutes(30);
    /** 같은 계정으로 복구 메일을 연달아 요청할 때의 최소 간격. 인증메일 재발송과 같은 정책이다. */
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    /** CSPRNG 로 뽑는 원문 토큰 길이. Base64url 로 43자가 된다. */
    private static final int RAW_TOKEN_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(AccountRecoveryService.class);

    private final UserMapper userMapper;
    private final AccountRecoveryTokenMapper accountRecoveryTokenMapper;
    private final EmailDispatchService emailDispatchService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${custom.server-url}")
    private String serverUrl;

    @Autowired
    public AccountRecoveryService(UserMapper userMapper,
                                  AccountRecoveryTokenMapper accountRecoveryTokenMapper,
                                  EmailDispatchService emailDispatchService) {
        this(userMapper, accountRecoveryTokenMapper, emailDispatchService,
                Clock.systemDefaultZone());
    }

    AccountRecoveryService(UserMapper userMapper,
                           AccountRecoveryTokenMapper accountRecoveryTokenMapper,
                           EmailDispatchService emailDispatchService,
                           Clock clock) {
        this.userMapper = userMapper;
        this.accountRecoveryTokenMapper = accountRecoveryTokenMapper;
        this.emailDispatchService = emailDispatchService;
        this.clock = clock;
    }

    /**
     * 복구 링크 발송 요청.
     *
     * <p>탈퇴 유예 안내 화면에서 본인이 직접 누르는 경로다. 이미 인증된 회원의 가입 이메일로만
     * 보내므로 주소를 다시 입력받지 않고, 결과도 그대로 알려준다.
     */
    @Transactional
    public RecoveryRequestOutcome requestRecoveryFor(Long userId) {
        if (userId == null) {
            return RecoveryRequestOutcome.NOT_ELIGIBLE;
        }

        LocalDateTime currentTime = now();
        User account = userMapper.findRecoverableWithdrawalById(userId, currentTime);
        if (account == null) {
            return RecoveryRequestOutcome.NOT_ELIGIBLE;
        }

        if (isWithinCooldown(userId, currentTime)) {
            log.info("Account recovery email skipped by cooldown: userId={}", userId);
            return RecoveryRequestOutcome.COOLDOWN;
        }

        LocalDateTime expiresAt = recoveryLinkExpiry(currentTime, account.getPurgeScheduledAt());
        if (!expiresAt.isAfter(currentTime)) {
            return RecoveryRequestOutcome.NOT_ELIGIBLE;
        }

        // 원문 토큰은 아래 메일 URL 밖으로 나가지 않는다. 로그에도 남기지 않는다.
        String rawToken = newRawToken();
        accountRecoveryTokenMapper.invalidateUnusedTokens(userId, currentTime);
        accountRecoveryTokenMapper.insertToken(
                userId, ResetTokenHasher.hash(rawToken), expiresAt);
        log.info("Account recovery token issued: userId={}, expiresAt={}", userId, expiresAt);

        dispatchRecoveryEmail(account.getUserEmail(), recoveryUrl(rawToken),
                remainingMinutes(currentTime, expiresAt));
        return RecoveryRequestOutcome.SENT;
    }

    /** 안내 화면이 보여줄 발송 결과. 본인 계정이라 결과를 감출 이유가 없다. */
    public enum RecoveryRequestOutcome {
        SENT,
        COOLDOWN,
        NOT_ELIGIBLE
    }

    /**
     * 링크가 지금 쓸 수 있는지만 본다.
     *
     * <p>메일 보안 스캐너나 링크 미리보기가 대신 열어 볼 수 있는 GET 단계에서 쓰는 검사라
     * 회원 상태도 토큰의 used_at 도 건드리지 않는다. 실제 복구는 사용자가 직접 누르는
     * POST 에서 {@link #confirmRecovery(String)} 가 같은 조건을 다시 확인하며 처리한다.
     */
    @Transactional(readOnly = true)
    public boolean isRecoveryLinkUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        LocalDateTime currentTime = now();
        AccountRecoveryToken token = accountRecoveryTokenMapper.findUsableByTokenHash(
                ResetTokenHasher.hash(rawToken.strip()), currentTime);
        if (token == null) {
            return false;
        }
        return userMapper.findRecoverableWithdrawalById(token.getUserId(), currentTime) != null;
    }

    /**
     * 복구 링크 확정.
     *
     * <p>회원 행을 잠그고 토큰과 계정 상태를 다시 확인한 뒤 한 트랜잭션에서 되돌린다.
     * 확인 화면의 GET 검사 결과는 신뢰하지 않고 여기서 전부 다시 본다.
     * 자동 로그인은 하지 않는다.
     */
    @Transactional
    public boolean confirmRecovery(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        LocalDateTime currentTime = now();
        AccountRecoveryToken token = accountRecoveryTokenMapper.findUsableByTokenHash(
                ResetTokenHasher.hash(rawToken.strip()), currentTime);
        if (token == null) {
            return false;
        }

        // 링크가 아직 살아 있어도 유예기간이 지났으면 여기서 걸러진다.
        User account = userMapper.findRecoverableWithdrawalByIdForUpdate(
                token.getUserId(), currentTime);
        if (account == null) {
            return false;
        }

        // 먼저 토큰을 닫아 같은 링크의 동시/재사용을 막는다.
        if (accountRecoveryTokenMapper.markUsed(token.getId(), currentTime) != 1) {
            return false;
        }

        int restored = userMapper.restoreWithdrawalPendingAccount(
                account.getId(), UserStatus.ACTIVE, currentTime);
        if (restored != 1) {
            // 잠근 뒤 다시 확인했는데도 갱신되지 않았다면 토큰 사용 처리까지 되돌린다.
            throw new IllegalStateException(
                    "탈퇴 유예 계정을 복구하지 못했습니다. userId=" + account.getId());
        }

        log.info("Account recovery completed: userId={}", account.getId());
        return true;
    }

    /**
     * 링크는 계정의 복구 가능 시간보다 오래 살 수 없다.
     * 최종 파기까지 8분 남았다면 새로 받은 링크도 8분만 유효하다.
     */
    private LocalDateTime recoveryLinkExpiry(LocalDateTime currentTime,
                                             LocalDateTime purgeScheduledAt) {
        LocalDateTime defaultExpiry = currentTime.plus(RECOVERY_TOKEN_VALIDITY);
        if (purgeScheduledAt == null || !purgeScheduledAt.isBefore(defaultExpiry)) {
            return defaultExpiry;
        }
        return purgeScheduledAt;
    }

    private boolean isWithinCooldown(Long userId, LocalDateTime currentTime) {
        LocalDateTime latestIssuedAt = accountRecoveryTokenMapper.findLatestIssuedAt(userId);
        return latestIssuedAt != null
                && latestIssuedAt.plus(RESEND_COOLDOWN).isAfter(currentTime);
    }

    /** 안내 문구가 실제 만료보다 길어지지 않도록 올림이 아니라 남은 분을 그대로 쓴다. */
    private long remainingMinutes(LocalDateTime currentTime, LocalDateTime expiresAt) {
        return Math.max(1, ChronoUnit.MINUTES.between(currentTime, expiresAt));
    }

    private String recoveryUrl(String rawToken) {
        return serverUrl + "/users/recover-account/confirm?token=" + rawToken;
    }

    private String newRawToken() {
        byte[] raw = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /**
     * 메일 발송은 @Async 라 워커 스레드에서 locale 을 다시 읽을 수 없다.
     * 요청 스레드인 여기서 언어를 확정해 넘긴다.
     */
    private void dispatchRecoveryEmail(String recipient, String recoveryUrl, long validMinutes) {
        SupportedLanguage language = SupportedLanguage.fromLocale(LocaleContextHolder.getLocale())
                .orElse(SupportedLanguage.KOREAN);
        try {
            emailDispatchService.dispatchAccountRecoveryEmail(
                    recipient, recoveryUrl, validMinutes, language);
        } catch (RuntimeException exception) {
            log.error("Account recovery email could not be scheduled: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
