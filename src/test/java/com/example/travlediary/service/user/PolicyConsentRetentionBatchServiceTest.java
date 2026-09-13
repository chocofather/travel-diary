package com.example.travlediary.service.user;

import com.example.travlediary.repository.user.UserPolicyConsentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 최종 탈퇴 후 3년이 지난 동의 이력 정리 배치.
 * 대상 선별 조건 자체는 SQL 이 갖고 있어 {@code UserPolicyConsentMapperContractTest} 가 본다.
 * 여기에서는 기준 시각 계산과 batch/실패 처리를 본다.
 */
@ExtendWith(MockitoExtension.class)
class PolicyConsentRetentionBatchServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2029, 9, 13, 3, 30);
    /** 최종 탈퇴가 이 시각 이전에 끝난 회원만 대상이다. */
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 9, 13, 3, 30);

    @Mock
    private UserPolicyConsentMapper userPolicyConsentMapper;
    @Mock
    private PolicyConsentRetentionTransactionService policyConsentRetentionTransactionService;

    private PolicyConsentRetentionBatchService batchService() {
        return new PolicyConsentRetentionBatchService(
                userPolicyConsentMapper, policyConsentRetentionTransactionService);
    }

    /** 만료 기준은 실행 시각에서 3년 전이고, 한 번에 100명씩만 읽는다. */
    @Test
    void theBatchAsksForUsersPurgedBeforeTheThreeYearCutoffInPagesOfOneHundred() {
        when(userPolicyConsentMapper.findConsentRetentionExpiredUserIds(
                CUTOFF, PolicyConsentRetentionBatchService.BATCH_SIZE))
                .thenReturn(List.of(7L));
        when(policyConsentRetentionTransactionService.purgeConsentsOf(7L, CUTOFF)).thenReturn(4);

        assertThat(batchService().purgeExpiredConsents(NOW)).isEqualTo(1);

        verify(userPolicyConsentMapper).findConsentRetentionExpiredUserIds(CUTOFF, 100);
        verify(policyConsentRetentionTransactionService).purgeConsentsOf(7L, CUTOFF);
    }

    /** 회원 한 명의 이력이 여러 건이어도 한 번의 회원 단위 삭제로 모두 사라진다. */
    @Test
    void everyConsentRowOfATargetUserGoesInOneDelete() {
        when(userPolicyConsentMapper.findConsentRetentionExpiredUserIds(
                CUTOFF, PolicyConsentRetentionBatchService.BATCH_SIZE))
                .thenReturn(List.of(7L));
        when(policyConsentRetentionTransactionService.purgeConsentsOf(7L, CUTOFF)).thenReturn(9);

        assertThat(batchService().purgeExpiredConsents(NOW)).isEqualTo(1);

        // 날짜별로 나눠 부르지 않는다. 회원당 한 번이다.
        verify(policyConsentRetentionTransactionService, org.mockito.Mockito.times(1))
                .purgeConsentsOf(7L, CUTOFF);
    }

    @Test
    void nothingToPurgeDoesNotOpenAnyTransaction() {
        when(userPolicyConsentMapper.findConsentRetentionExpiredUserIds(
                CUTOFF, PolicyConsentRetentionBatchService.BATCH_SIZE))
                .thenReturn(List.of());

        assertThat(batchService().purgeExpiredConsents(NOW)).isZero();

        verifyNoInteractions(policyConsentRetentionTransactionService);
    }

    /** 목록을 읽은 뒤 조건이 깨진 회원은 한 행도 지우지 않고 건너뛴다. */
    @Test
    void aUserThatIsNoLongerEligibleIsSkipped() {
        when(userPolicyConsentMapper.findConsentRetentionExpiredUserIds(
                CUTOFF, PolicyConsentRetentionBatchService.BATCH_SIZE))
                .thenReturn(List.of(7L, 8L));
        when(policyConsentRetentionTransactionService.purgeConsentsOf(7L, CUTOFF)).thenReturn(0);
        when(policyConsentRetentionTransactionService.purgeConsentsOf(8L, CUTOFF)).thenReturn(2);

        assertThat(batchService().purgeExpiredConsents(NOW)).isEqualTo(1);
    }

    /** 한 명이 실패해도 나머지는 계속 처리한다. 실패한 회원은 다음 주기가 다시 집는다. */
    @Test
    void oneFailureDoesNotStopTheRestOfTheBatch() {
        when(userPolicyConsentMapper.findConsentRetentionExpiredUserIds(
                CUTOFF, PolicyConsentRetentionBatchService.BATCH_SIZE))
                .thenReturn(List.of(7L, 8L, 9L));
        when(policyConsentRetentionTransactionService.purgeConsentsOf(7L, CUTOFF)).thenReturn(1);
        when(policyConsentRetentionTransactionService.purgeConsentsOf(8L, CUTOFF))
                .thenThrow(new IllegalStateException("db down"));
        when(policyConsentRetentionTransactionService.purgeConsentsOf(9L, CUTOFF)).thenReturn(1);

        assertThat(batchService().purgeExpiredConsents(NOW)).isEqualTo(2);

        verify(policyConsentRetentionTransactionService).purgeConsentsOf(9L, CUTOFF);
    }

    @Test
    void aMissingRunTimeDoesNothing() {
        assertThat(batchService().purgeExpiredConsents(null)).isZero();

        verify(userPolicyConsentMapper, never())
                .findConsentRetentionExpiredUserIds(any(), anyInt());
        verify(policyConsentRetentionTransactionService, never())
                .purgeConsentsOf(anyLong(), any());
    }

    /** 스케줄러는 하루 한 번, 기존 배치와 겹치지 않는 새벽 시간에 돈다. */
    @Test
    void theSchedulerRunsOnceADayOutsideTheOtherBatchSlots() throws Exception {
        String cron = PolicyConsentRetentionScheduler.class
                .getDeclaredMethod("purgeExpiredPolicyConsents")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class)
                .cron();

        // 매시 정각/:10/:20 배치와 문의 보존기간 배치(04:40) 어느 쪽과도 겹치지 않는다.
        assertThat(cron).isEqualTo("0 30 3 * * *");
    }

    /** Clock 을 주입하면 3년 경계를 임의 시각으로 돌려볼 수 있다. */
    @Test
    void theSchedulerUsesTheInjectedClockAsTheRunTime() {
        java.time.Clock fixed = java.time.Clock.fixed(
                NOW.atZone(java.time.ZoneId.systemDefault()).toInstant(),
                java.time.ZoneId.systemDefault());
        PolicyConsentRetentionBatchService batchService =
                org.mockito.Mockito.mock(PolicyConsentRetentionBatchService.class);

        new PolicyConsentRetentionScheduler(batchService, fixed).purgeExpiredPolicyConsents();

        verify(batchService).purgeExpiredConsents(NOW);
    }
}
