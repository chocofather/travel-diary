package com.example.travlediary.service.email;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.user.EmailPolicy;
import com.example.travlediary.service.user.RegistrationValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class EmailVerificationService {

    static final Duration TOKEN_VALIDITY = Duration.ofHours(24);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final UserMapper userMapper;
    private final EmailDispatchService emailDispatchService;
    private final Clock clock;

    @Autowired
    public EmailVerificationService(UserMapper userMapper,
                                    EmailDispatchService emailDispatchService) {
        this(userMapper, emailDispatchService, Clock.systemDefaultZone());
    }

    EmailVerificationService(UserMapper userMapper,
                             EmailDispatchService emailDispatchService,
                             Clock clock) {
        this.userMapper = userMapper;
        this.emailDispatchService = emailDispatchService;
        this.clock = clock;
    }

    public void initializeVerification(User user) {
        LocalDateTime issuedAt = now();
        user.setVerificationToken(newToken());
        user.setVerificationTokenExp(issuedAt.plus(TOKEN_VALIDITY));
        user.setVerificationRequestedAt(issuedAt);
    }

    public boolean requestInitialVerification(User user) {
        return dispatch(user);
    }

    public VerificationOutcome verify(String token) {
        if (token == null || token.isBlank()) {
            return VerificationOutcome.invalid();
        }

        User user = userMapper.findPendingVerificationByToken(token.strip());
        if (!isPending(user)) {
            return VerificationOutcome.invalid();
        }

        LocalDateTime expiration = user.getVerificationTokenExp();
        if (expiration == null || !expiration.isAfter(now())) {
            return VerificationOutcome.expired(user.getUserEmail());
        }

        int updated = userMapper.activatePendingUser(
                user.getId(), user.getVerificationToken(), now());
        return updated == 1
                ? VerificationOutcome.success(user.getUserEmail())
                : VerificationOutcome.invalid();
    }

    public ResendOutcome resend(String email) {
        final String normalizedEmail;
        try {
            normalizedEmail = EmailPolicy.normalizeAndValidate(email);
        } catch (RegistrationValidationException ignored) {
            return ResendOutcome.notEligible();
        }

        User user = userMapper.findPendingVerificationByEmail(normalizedEmail);
        if (!isPending(user)) {
            return ResendOutcome.notEligible();
        }

        LocalDateTime issuedAt = now();
        long remainingSeconds = remainingCooldownSeconds(
                user.getVerificationRequestedAt(), issuedAt);
        if (remainingSeconds > 0) {
            return ResendOutcome.cooldown(remainingSeconds);
        }

        String token = newToken();
        LocalDateTime expiration = issuedAt.plus(TOKEN_VALIDITY);
        int updated = userMapper.refreshVerificationToken(
                user.getId(), token, expiration, issuedAt, issuedAt.minus(RESEND_COOLDOWN));
        if (updated != 1) {
            return ResendOutcome.cooldown(RESEND_COOLDOWN.toSeconds());
        }

        user.setVerificationToken(token);
        user.setVerificationTokenExp(expiration);
        user.setVerificationRequestedAt(issuedAt);
        return dispatch(user) ? ResendOutcome.sent() : ResendOutcome.deliveryFailed();
    }

    public WaitingState getWaitingState(String email) {
        if (email == null || email.isBlank()) {
            return WaitingState.unavailable();
        }

        User user;
        try {
            user = userMapper.findPendingVerificationByEmail(
                    EmailPolicy.normalizeAndValidate(email));
        } catch (RuntimeException ignored) {
            return WaitingState.unavailable();
        }
        if (!isPending(user)) {
            return WaitingState.unavailable();
        }

        return new WaitingState(
                true,
                EmailPolicy.mask(user.getUserEmail()),
                remainingCooldownSeconds(user.getVerificationRequestedAt(), now()));
    }

    private boolean dispatch(User user) {
        try {
            emailDispatchService.dispatchVerificationEmail(
                    user.getId(), user.getUserEmail(), user.getVerificationToken());
            return true;
        } catch (RuntimeException exception) {
            log.error("Verification email dispatch was rejected: "
                            + "userId={}, recipient={}, exceptionType={}",
                    user.getId(), EmailPolicy.mask(user.getUserEmail()),
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    private boolean isPending(User user) {
        return user != null
                && user.getStatus() == UserStatus.INACTIVE
                && user.getDeletedAt() == null;
    }

    private long remainingCooldownSeconds(LocalDateTime requestedAt, LocalDateTime currentTime) {
        if (requestedAt == null) {
            return 0;
        }
        LocalDateTime availableAt = requestedAt.plus(RESEND_COOLDOWN);
        if (!availableAt.isAfter(currentTime)) {
            return 0;
        }
        return Math.max(1, ChronoUnit.SECONDS.between(currentTime, availableAt));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private String newToken() {
        return UUID.randomUUID().toString();
    }

    public enum VerificationStatus {
        SUCCESS,
        EXPIRED,
        INVALID
    }

    public record VerificationOutcome(VerificationStatus status, String email) {

        static VerificationOutcome success(String email) {
            return new VerificationOutcome(VerificationStatus.SUCCESS, email);
        }

        static VerificationOutcome expired(String email) {
            return new VerificationOutcome(VerificationStatus.EXPIRED, email);
        }

        static VerificationOutcome invalid() {
            return new VerificationOutcome(VerificationStatus.INVALID, null);
        }
    }

    public enum ResendStatus {
        SENT,
        COOLDOWN,
        NOT_ELIGIBLE,
        DELIVERY_FAILED
    }

    public record ResendOutcome(ResendStatus status, long remainingSeconds) {

        static ResendOutcome sent() {
            return new ResendOutcome(ResendStatus.SENT, RESEND_COOLDOWN.toSeconds());
        }

        static ResendOutcome cooldown(long remainingSeconds) {
            return new ResendOutcome(ResendStatus.COOLDOWN, remainingSeconds);
        }

        static ResendOutcome notEligible() {
            return new ResendOutcome(ResendStatus.NOT_ELIGIBLE, 0);
        }

        static ResendOutcome deliveryFailed() {
            return new ResendOutcome(ResendStatus.DELIVERY_FAILED, RESEND_COOLDOWN.toSeconds());
        }
    }

    public record WaitingState(boolean available, String maskedEmail, long remainingSeconds) {

        static WaitingState unavailable() {
            return new WaitingState(false, "", 0);
        }
    }
}
