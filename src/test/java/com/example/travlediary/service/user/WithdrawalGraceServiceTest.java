package com.example.travlediary.service.user;

import com.example.travlediary.model.AccountPurgeJobStatus;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 탈퇴 유예 판정의 경계.
 *
 * <p>이 버그의 핵심은 "유예가 끝났는지"를 배치 실행 여부로 판단한 것이었다.
 * 기준은 언제나 purge_scheduled_at 이고, 정각 배치가 아직 돌지 않은 구간에서도
 * 요청 시점에 바로 유예 종료로 판정해야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WithdrawalGraceServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 10, 30);

    @Mock
    private UserMapper userMapper;
    @Mock
    private AccountPurgeTransactionService accountPurgeTransactionService;

    private WithdrawalGraceService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        service = new WithdrawalGraceService(userMapper, accountPurgeTransactionService, clock);
        when(accountPurgeTransactionService.purgeOne(any(), any(), any()))
                .thenReturn(new AccountPurgeTransactionService.PurgeOutcome(
                        true, 100L, 0, AccountPurgeJobStatus.COMPLETED));
    }

    @Test
    void anAccountWithTimeLeftIsStillInGraceAndIsNeverPurged() {
        givenWithdrawalPending(NOW.plusMinutes(1));

        assertThat(service.resolveAccess(USER_ID, null))
                .isEqualTo(WithdrawalGraceService.Outcome.IN_GRACE);
        verifyNoInteractions(accountPurgeTransactionService);
    }

    /** 정확히 파기 예정 시각이면 이미 끝난 것으로 본다. 배치의 대상 조건과 같은 경계다. */
    @Test
    void theExactPurgeInstantIsAlreadyOutOfGrace() {
        givenWithdrawalPending(NOW);

        assertThat(service.resolveAccess(USER_ID, null))
                .isEqualTo(WithdrawalGraceService.Outcome.GRACE_ENDED);
    }

    @Test
    void aPastPurgeInstantIsOutOfGraceEvenBeforeTheSchedulerRuns() {
        givenWithdrawalPending(NOW.minusHours(1));

        assertThat(service.resolveAccess(USER_ID, null))
                .isEqualTo(WithdrawalGraceService.Outcome.GRACE_ENDED);
        verify(accountPurgeTransactionService).purgeOne(eq(USER_ID), eq(NOW), any());
    }

    /** 일정이 비어 있으면 끝났다고 단정할 근거가 없다. 파괴적 처리라 판정을 미룬다. */
    @Test
    void aMissingScheduleIsNotTreatedAsAnEndedGrace() {
        givenWithdrawalPending(null);

        assertThat(service.resolveAccess(USER_ID, null))
                .isEqualTo(WithdrawalGraceService.Outcome.IN_GRACE);
        verifyNoInteractions(accountPurgeTransactionService);
    }

    @Test
    void anOrdinaryMemberIsNotPendingAndIsNeverTouched() {
        when(userMapper.findStatusById(USER_ID)).thenReturn(UserStatus.ACTIVE);

        assertThat(service.resolveAccess(USER_ID, null))
                .isEqualTo(WithdrawalGraceService.Outcome.NOT_PENDING);
        verify(userMapper, never()).findWithdrawalPendingById(USER_ID);
        verifyNoInteractions(accountPurgeTransactionService);
    }

    /** 복구가 먼저 끝나 상태가 바뀐 경우. 잠금 조회 전에 이미 대상이 아니다. */
    @Test
    void anAccountRecoveredBetweenTheTwoReadsIsNotPending() {
        when(userMapper.findStatusById(USER_ID)).thenReturn(UserStatus.WITHDRAWAL_PENDING);
        when(userMapper.findWithdrawalPendingById(USER_ID)).thenReturn(null);

        assertThat(service.resolveAccess(USER_ID, null))
                .isEqualTo(WithdrawalGraceService.Outcome.NOT_PENDING);
        verifyNoInteractions(accountPurgeTransactionService);
    }

    /** 일반 로그인에는 지금 인증한 provider 가 없으므로 기존 파기 정책을 그대로 쓴다. */
    @Test
    void aFormLoginFinalizationKeepsTheScheduledUnlinkPolicy() {
        givenWithdrawalPending(NOW.minusDays(1));

        service.resolveAccess(USER_ID, null);

        assertThat(capturedOptions().reauthenticatedProvider()).isNull();
    }

    /** 소셜 재진입이면 그 provider 만 연결 해제 대상에서 뺀다. */
    @Test
    void aSocialReAuthenticationSuppressesUnlinkForThatProviderOnly() {
        givenWithdrawalPending(NOW.minusDays(1));

        service.resolveAccess(USER_ID, SocialProvider.KAKAO);

        AccountPurgeOptions options = capturedOptions();
        assertThat(options.suppressesUnlinkFor(SocialProvider.KAKAO)).isTrue();
        assertThat(options.suppressesUnlinkFor(SocialProvider.GOOGLE)).isFalse();
    }

    /**
     * 파기가 실패해도 유예가 끝났다는 판정은 그대로다.
     * 여기서 예외를 올리면 사용자는 아무것도 할 수 없는 화면에 그대로 갇힌다.
     */
    @Test
    void aFailedPurgeStillReportsTheGraceAsEnded() {
        givenWithdrawalPending(NOW.minusDays(1));
        when(accountPurgeTransactionService.purgeOne(any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        assertThat(service.resolveAccess(USER_ID, null))
                .isEqualTo(WithdrawalGraceService.Outcome.GRACE_ENDED);
    }

    @Test
    void anAnonymousRequestIsNotPending() {
        assertThat(service.resolveAccess(null, null))
                .isEqualTo(WithdrawalGraceService.Outcome.NOT_PENDING);
        verifyNoInteractions(userMapper, accountPurgeTransactionService);
    }

    private void givenWithdrawalPending(LocalDateTime purgeScheduledAt) {
        User account = new User();
        account.setId(USER_ID);
        account.setStatus(UserStatus.WITHDRAWAL_PENDING);
        account.setPurgeScheduledAt(purgeScheduledAt);
        when(userMapper.findStatusById(USER_ID)).thenReturn(UserStatus.WITHDRAWAL_PENDING);
        when(userMapper.findWithdrawalPendingById(USER_ID)).thenReturn(account);
    }

    private AccountPurgeOptions capturedOptions() {
        ArgumentCaptor<AccountPurgeOptions> captor =
                ArgumentCaptor.forClass(AccountPurgeOptions.class);
        verify(accountPurgeTransactionService).purgeOne(eq(USER_ID), eq(NOW), captor.capture());
        return captor.getValue();
    }
}
