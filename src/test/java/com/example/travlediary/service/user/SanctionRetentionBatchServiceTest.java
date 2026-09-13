package com.example.travlediary.service.user;

import com.example.travlediary.repository.user.BlockedEmailMapper;
import com.example.travlediary.repository.user.UserSanctionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 보관기간이 끝난 제재 이력/재가입 차단 기록 정리 배치.
 * 대상 선별 조건 자체는 SQL 이 갖고 있어 {@code SanctionRetentionMapperContractTest} 가 본다.
 * 여기에서는 기준 시각 계산과 batch/실패 처리를 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SanctionRetentionBatchServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2029, 9, 13, 3, 50);
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 9, 13, 3, 50);

    @Mock
    private UserSanctionMapper userSanctionMapper;
    @Mock
    private BlockedEmailMapper blockedEmailMapper;
    @Mock
    private SanctionRetentionTransactionService sanctionRetentionTransactionService;

    private SanctionRetentionBatchService batchService() {
        return new SanctionRetentionBatchService(
                userSanctionMapper, blockedEmailMapper, sanctionRetentionTransactionService);
    }

    private void noSanctionsDue() {
        when(userSanctionMapper.findRetentionExpiredSanctionIds(any(), anyInt()))
                .thenReturn(List.of());
    }

    private void noBlocksDue() {
        when(blockedEmailMapper.findReSignupBlockRetentionExpiredUserIds(any(), anyInt()))
                .thenReturn(List.of());
    }

    /** 두 정리 모두 실행 시각에서 3년 전을 기준으로 하고, 한 번에 100건씩만 읽는다. */
    @Test
    void bothPassesUseTheThreeYearCutoffInPagesOfOneHundred() {
        when(userSanctionMapper.findRetentionExpiredSanctionIds(
                CUTOFF, SanctionRetentionBatchService.BATCH_SIZE)).thenReturn(List.of(11L));
        when(sanctionRetentionTransactionService.purgeSanction(11L, CUTOFF)).thenReturn(true);
        when(blockedEmailMapper.findReSignupBlockRetentionExpiredUserIds(
                CUTOFF, SanctionRetentionBatchService.BATCH_SIZE)).thenReturn(List.of(22L));
        when(sanctionRetentionTransactionService.purgeReSignupBlocksOf(22L, CUTOFF)).thenReturn(1);

        assertThat(batchService().purgeExpiredSanctionRecords(NOW)).isEqualTo(2);

        verify(userSanctionMapper).findRetentionExpiredSanctionIds(CUTOFF, 100);
        verify(blockedEmailMapper).findReSignupBlockRetentionExpiredUserIds(CUTOFF, 100);
    }

    /** 제재도 차단 기록도 없는 회원만 있으면 트랜잭션을 아예 열지 않는다. */
    @Test
    void nothingDueOpensNoTransaction() {
        noSanctionsDue();
        noBlocksDue();

        assertThat(batchService().purgeExpiredSanctionRecords(NOW)).isZero();

        verifyNoInteractions(sanctionRetentionTransactionService);
    }

    /** 목록을 읽은 뒤 제재가 다시 걸렸다면 DELETE 가 0 행을 지우고 건너뛴다. */
    @Test
    void aSanctionThatIsNoLongerEligibleIsSkipped() {
        when(userSanctionMapper.findRetentionExpiredSanctionIds(
                CUTOFF, SanctionRetentionBatchService.BATCH_SIZE)).thenReturn(List.of(11L, 12L));
        when(sanctionRetentionTransactionService.purgeSanction(11L, CUTOFF)).thenReturn(false);
        when(sanctionRetentionTransactionService.purgeSanction(12L, CUTOFF)).thenReturn(true);
        noBlocksDue();

        assertThat(batchService().purgeExpiredSanctionRecords(NOW)).isEqualTo(1);
    }

    /** 한 건이 실패해도 나머지는 계속 처리한다. 실패한 기록은 다음 주기가 다시 집는다. */
    @Test
    void oneFailureDoesNotStopTheRestOfTheBatch() {
        when(userSanctionMapper.findRetentionExpiredSanctionIds(
                CUTOFF, SanctionRetentionBatchService.BATCH_SIZE))
                .thenReturn(List.of(11L, 12L, 13L));
        when(sanctionRetentionTransactionService.purgeSanction(11L, CUTOFF)).thenReturn(true);
        when(sanctionRetentionTransactionService.purgeSanction(12L, CUTOFF))
                .thenThrow(new IllegalStateException("db down"));
        when(sanctionRetentionTransactionService.purgeSanction(13L, CUTOFF)).thenReturn(true);
        noBlocksDue();

        assertThat(batchService().purgeExpiredSanctionRecords(NOW)).isEqualTo(2);

        verify(sanctionRetentionTransactionService).purgeSanction(13L, CUTOFF);
    }

    /** 제재 이력 정리가 실패해도 재가입 차단 정리는 그대로 돈다. 두 정리는 독립이다. */
    @Test
    void aFailureInTheSanctionPassStillLetsTheBlockPassRun() {
        when(userSanctionMapper.findRetentionExpiredSanctionIds(
                CUTOFF, SanctionRetentionBatchService.BATCH_SIZE)).thenReturn(List.of(11L));
        when(sanctionRetentionTransactionService.purgeSanction(11L, CUTOFF))
                .thenThrow(new IllegalStateException("db down"));
        when(blockedEmailMapper.findReSignupBlockRetentionExpiredUserIds(
                CUTOFF, SanctionRetentionBatchService.BATCH_SIZE)).thenReturn(List.of(22L));
        when(sanctionRetentionTransactionService.purgeReSignupBlocksOf(22L, CUTOFF)).thenReturn(2);

        assertThat(batchService().purgeExpiredSanctionRecords(NOW)).isEqualTo(1);
    }

    /** 한 회원의 차단 기록이 여러 건이어도 회원 단위 한 번의 삭제로 끝난다. */
    @Test
    void everyBlockOfATargetUserGoesInOneDelete() {
        noSanctionsDue();
        when(blockedEmailMapper.findReSignupBlockRetentionExpiredUserIds(
                CUTOFF, SanctionRetentionBatchService.BATCH_SIZE)).thenReturn(List.of(22L));
        when(sanctionRetentionTransactionService.purgeReSignupBlocksOf(22L, CUTOFF)).thenReturn(3);

        assertThat(batchService().purgeExpiredSanctionRecords(NOW)).isEqualTo(1);

        verify(sanctionRetentionTransactionService, org.mockito.Mockito.times(1))
                .purgeReSignupBlocksOf(22L, CUTOFF);
    }

    /**
     * 재가입 차단 기록을 먼저 지운다. 최종 탈퇴 회원의 적용중 영구제재는 차단 기록이 남아 있는
     * 동안 SQL 에서 제외되므로, 이 순서라야 같은 실행 안에서 제재까지 정리된다.
     */
    @Test
    void theBlockPassRunsBeforeTheSanctionPass() {
        when(blockedEmailMapper.findReSignupBlockRetentionExpiredUserIds(
                CUTOFF, SanctionRetentionBatchService.BATCH_SIZE)).thenReturn(List.of(22L));
        when(sanctionRetentionTransactionService.purgeReSignupBlocksOf(22L, CUTOFF)).thenReturn(1);
        when(userSanctionMapper.findRetentionExpiredSanctionIds(
                CUTOFF, SanctionRetentionBatchService.BATCH_SIZE)).thenReturn(List.of(11L));
        when(sanctionRetentionTransactionService.purgeSanction(11L, CUTOFF)).thenReturn(true);

        batchService().purgeExpiredSanctionRecords(NOW);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(
                blockedEmailMapper, sanctionRetentionTransactionService, userSanctionMapper);
        inOrder.verify(blockedEmailMapper)
                .findReSignupBlockRetentionExpiredUserIds(CUTOFF, 100);
        inOrder.verify(sanctionRetentionTransactionService).purgeReSignupBlocksOf(22L, CUTOFF);
        inOrder.verify(userSanctionMapper).findRetentionExpiredSanctionIds(CUTOFF, 100);
        inOrder.verify(sanctionRetentionTransactionService).purgeSanction(11L, CUTOFF);
    }

    @Test
    void aMissingRunTimeDoesNothing() {
        assertThat(batchService().purgeExpiredSanctionRecords(null)).isZero();

        verify(userSanctionMapper, never()).findRetentionExpiredSanctionIds(any(), anyInt());
        verify(blockedEmailMapper, never())
                .findReSignupBlockRetentionExpiredUserIds(any(), anyInt());
        verify(sanctionRetentionTransactionService, never()).purgeSanction(anyLong(), any());
        verify(sanctionRetentionTransactionService, never())
                .purgeReSignupBlocksOf(anyLong(), any());
    }

    /** 스케줄러는 하루 한 번, 기존 배치와 겹치지 않는 새벽 시간에 돈다. */
    @Test
    void theSchedulerRunsOnceADayOutsideTheOtherBatchSlots() throws Exception {
        String cron = SanctionRetentionScheduler.class
                .getDeclaredMethod("purgeExpiredSanctionRecords")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class)
                .cron();

        // 매시 정각/:10/:20, 동의 이력(03:30), 문의 보존기간(04:40) 어느 쪽과도 겹치지 않는다.
        assertThat(cron).isEqualTo("0 50 3 * * *");
    }

    /** Clock 을 주입하면 3년 경계를 임의 시각으로 돌려볼 수 있다. */
    @Test
    void theSchedulerUsesTheInjectedClockAsTheRunTime() {
        Clock fixed = Clock.fixed(
                NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        SanctionRetentionBatchService batchService = mock(SanctionRetentionBatchService.class);

        new SanctionRetentionScheduler(batchService, fixed).purgeExpiredSanctionRecords();

        verify(batchService).purgeExpiredSanctionRecords(NOW);
    }
}
