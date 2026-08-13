package com.example.travlediary.service.email;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-13T01:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.of(2026, 8, 13, 1, 0);

    @Mock private UserMapper userMapper;
    @Mock private EmailDispatchService emailDispatchService;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(
                userMapper, emailDispatchService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void initialTokenIsAUuidAndExpiresAfterTwentyFourHours() {
        User user = pendingUser();

        service.initializeVerification(user);

        assertThat(user.getVerificationToken()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(user.getVerificationRequestedAt()).isEqualTo(LOCAL_NOW);
        assertThat(user.getVerificationTokenExp()).isEqualTo(LOCAL_NOW.plusHours(24));
    }

    @Test
    void validPendingTokenActivatesExactlyTheMatchingUser() {
        User user = pendingUser();
        user.setVerificationToken("valid-token");
        user.setVerificationTokenExp(LOCAL_NOW.plusMinutes(1));
        when(userMapper.findPendingVerificationByToken("valid-token")).thenReturn(user);
        when(userMapper.activatePendingUser(7L, "valid-token", LOCAL_NOW)).thenReturn(1);

        EmailVerificationService.VerificationOutcome outcome = service.verify("valid-token");

        assertThat(outcome.status()).isEqualTo(EmailVerificationService.VerificationStatus.SUCCESS);
        verify(userMapper).activatePendingUser(7L, "valid-token", LOCAL_NOW);
    }

    @Test
    void expiredTokenNeverActivatesTheAccount() {
        User user = pendingUser();
        user.setVerificationToken("expired-token");
        user.setVerificationTokenExp(LOCAL_NOW);
        when(userMapper.findPendingVerificationByToken("expired-token")).thenReturn(user);

        EmailVerificationService.VerificationOutcome outcome = service.verify("expired-token");

        assertThat(outcome.status()).isEqualTo(EmailVerificationService.VerificationStatus.EXPIRED);
        verify(userMapper, never()).activatePendingUser(any(), any(), any());
    }

    @Test
    void unknownTokenIsInvalid() {
        EmailVerificationService.VerificationOutcome outcome = service.verify("unknown");

        assertThat(outcome.status()).isEqualTo(EmailVerificationService.VerificationStatus.INVALID);
        verify(userMapper, never()).activatePendingUser(any(), any(), any());
    }

    @Test
    void activeOrDeletedAccountCannotBeActivatedThroughAStoredToken() {
        User active = pendingUser();
        active.setStatus(UserStatus.ACTIVE);
        when(userMapper.findPendingVerificationByToken("active-token")).thenReturn(active);

        User deleted = pendingUser();
        deleted.setStatus(UserStatus.DEACTIVATED);
        deleted.setDeletedAt(Timestamp.valueOf(LOCAL_NOW.minusDays(1)));
        when(userMapper.findPendingVerificationByToken("deleted-token")).thenReturn(deleted);

        assertThat(service.verify("active-token").status())
                .isEqualTo(EmailVerificationService.VerificationStatus.INVALID);
        assertThat(service.verify("deleted-token").status())
                .isEqualTo(EmailVerificationService.VerificationStatus.INVALID);
        verify(userMapper, never()).activatePendingUser(any(), any(), any());
    }

    @Test
    void resendReplacesTheOldTokenAndIssuesANewTwentyFourHourExpiration() {
        User user = pendingUser();
        user.setVerificationToken("old-token");
        user.setVerificationRequestedAt(LOCAL_NOW.minusSeconds(61));
        when(userMapper.findPendingVerificationByEmail("member@gmail.com")).thenReturn(user);
        when(userMapper.refreshVerificationToken(
                eq(7L), any(), eq(LOCAL_NOW.plusHours(24)), eq(LOCAL_NOW),
                eq(LOCAL_NOW.minusSeconds(60)))).thenReturn(1);

        EmailVerificationService.ResendOutcome outcome = service.resend(" MEMBER@GMAIL.COM ");

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).refreshVerificationToken(
                eq(7L), tokenCaptor.capture(), eq(LOCAL_NOW.plusHours(24)), eq(LOCAL_NOW),
                eq(LOCAL_NOW.minusSeconds(60)));
        assertThat(tokenCaptor.getValue()).isNotEqualTo("old-token");
        verify(emailDispatchService).dispatchVerificationEmail(
                7L, "member@gmail.com", tokenCaptor.getValue());
        assertThat(outcome.status()).isEqualTo(EmailVerificationService.ResendStatus.SENT);
    }

    @Test
    void legacyPendingAccountWithoutTokenExpirationOrRequestTimeCanResend() {
        User user = pendingUser();
        user.setVerificationToken(null);
        user.setVerificationTokenExp(null);
        user.setVerificationRequestedAt(null);
        when(userMapper.findPendingVerificationByEmail("member@gmail.com")).thenReturn(user);
        when(userMapper.refreshVerificationToken(
                eq(7L), any(), eq(LOCAL_NOW.plusHours(24)), eq(LOCAL_NOW),
                eq(LOCAL_NOW.minusSeconds(60)))).thenReturn(1);

        EmailVerificationService.ResendOutcome outcome = service.resend("member@gmail.com");

        assertThat(outcome.status()).isEqualTo(EmailVerificationService.ResendStatus.SENT);
        assertThat(user.getVerificationToken()).isNotBlank();
        assertThat(user.getVerificationTokenExp()).isEqualTo(LOCAL_NOW.plusHours(24));
        assertThat(user.getVerificationRequestedAt()).isEqualTo(LOCAL_NOW);
        verify(emailDispatchService).dispatchVerificationEmail(
                7L, "member@gmail.com", user.getVerificationToken());
    }

    @Test
    void expiredVerificationTokenDoesNotPreventResend() {
        User user = pendingUser();
        user.setVerificationToken("expired-token");
        user.setVerificationTokenExp(LOCAL_NOW.minusDays(1));
        user.setVerificationRequestedAt(LOCAL_NOW.minusDays(1));
        when(userMapper.findPendingVerificationByEmail("member@gmail.com")).thenReturn(user);
        when(userMapper.refreshVerificationToken(any(), any(), any(), any(), any())).thenReturn(1);

        EmailVerificationService.ResendOutcome outcome = service.resend("member@gmail.com");

        assertThat(outcome.status()).isEqualTo(EmailVerificationService.ResendStatus.SENT);
        assertThat(user.getVerificationToken()).isNotEqualTo("expired-token");
        assertThat(user.getVerificationTokenExp()).isEqualTo(LOCAL_NOW.plusHours(24));
    }

    @Test
    void resendBeforeCooldownDoesNotChangeTokenOrSendMail() {
        User user = pendingUser();
        user.setVerificationRequestedAt(LOCAL_NOW.minusSeconds(10));
        when(userMapper.findPendingVerificationByEmail("member@gmail.com")).thenReturn(user);

        EmailVerificationService.ResendOutcome outcome = service.resend("member@gmail.com");

        assertThat(outcome.status()).isEqualTo(EmailVerificationService.ResendStatus.COOLDOWN);
        assertThat(outcome.remainingSeconds()).isEqualTo(50);
        verify(userMapper, never()).refreshVerificationToken(any(), any(), any(), any(), any());
        verify(emailDispatchService, never()).dispatchVerificationEmail(any(), any(), any());
    }

    @Test
    void nonPendingAndUnknownAccountsAreNotEligibleForResend() {
        User active = pendingUser();
        active.setStatus(UserStatus.ACTIVE);
        when(userMapper.findPendingVerificationByEmail("active@gmail.com")).thenReturn(active);

        User suspended = pendingUser();
        suspended.setStatus(UserStatus.SUSPENDED);
        when(userMapper.findPendingVerificationByEmail("paused@gmail.com")).thenReturn(suspended);

        User deactivated = pendingUser();
        deactivated.setStatus(UserStatus.DEACTIVATED);
        when(userMapper.findPendingVerificationByEmail("closed@gmail.com")).thenReturn(deactivated);

        User deleted = pendingUser();
        deleted.setDeletedAt(Timestamp.valueOf(LOCAL_NOW.minusDays(1)));
        when(userMapper.findPendingVerificationByEmail("deleted@gmail.com")).thenReturn(deleted);

        assertThat(service.resend("active@gmail.com").status())
                .isEqualTo(EmailVerificationService.ResendStatus.NOT_ELIGIBLE);
        assertThat(service.resend("paused@gmail.com").status())
                .isEqualTo(EmailVerificationService.ResendStatus.NOT_ELIGIBLE);
        assertThat(service.resend("closed@gmail.com").status())
                .isEqualTo(EmailVerificationService.ResendStatus.NOT_ELIGIBLE);
        assertThat(service.resend("deleted@gmail.com").status())
                .isEqualTo(EmailVerificationService.ResendStatus.NOT_ELIGIBLE);
        assertThat(service.resend("unknown@gmail.com").status())
                .isEqualTo(EmailVerificationService.ResendStatus.NOT_ELIGIBLE);
        verify(emailDispatchService, never()).dispatchVerificationEmail(any(), any(), any());
    }

    @Test
    void losingTheConditionalRefreshRaceDoesNotSendAnotherEmail() {
        User user = pendingUser();
        user.setVerificationRequestedAt(LOCAL_NOW.minusMinutes(2));
        when(userMapper.findPendingVerificationByEmail("member@gmail.com")).thenReturn(user);
        when(userMapper.refreshVerificationToken(any(), any(), any(), any(), any())).thenReturn(0);

        EmailVerificationService.ResendOutcome outcome = service.resend("member@gmail.com");

        assertThat(outcome.status()).isEqualTo(EmailVerificationService.ResendStatus.COOLDOWN);
        verify(emailDispatchService, never()).dispatchVerificationEmail(any(), any(), any());
    }

    @Test
    void dispatchRejectionAfterTokenRefreshReturnsRecoverableStatus() {
        User user = pendingUser();
        user.setVerificationRequestedAt(LOCAL_NOW.minusMinutes(2));
        when(userMapper.findPendingVerificationByEmail("member@gmail.com")).thenReturn(user);
        when(userMapper.refreshVerificationToken(any(), any(), any(), any(), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new TaskRejectedException("mail queue is full"))
                .when(emailDispatchService).dispatchVerificationEmail(any(), any(), any());

        EmailVerificationService.ResendOutcome outcome = service.resend("member@gmail.com");

        assertThat(outcome.status())
                .isEqualTo(EmailVerificationService.ResendStatus.DELIVERY_FAILED);
        assertThat(user.getVerificationRequestedAt()).isEqualTo(LOCAL_NOW);
        verify(userMapper).refreshVerificationToken(any(), any(), any(), any(), any());
    }

    @Test
    void unexpectedDispatchFailureBecomesARecoverableInitialDeliveryResult() {
        User user = pendingUser();
        user.setVerificationToken("safe-token");
        org.mockito.Mockito.doThrow(new IllegalStateException("template rendering failed"))
                .when(emailDispatchService).dispatchVerificationEmail(any(), any(), any());

        boolean sent = service.requestInitialVerification(user);

        assertThat(sent).isFalse();
    }

    private User pendingUser() {
        User user = new User();
        user.setId(7L);
        user.setStatus(UserStatus.INACTIVE);
        user.setUserEmail("member@gmail.com");
        return user;
    }
}
