package com.example.travlediary.service.inquiry;

import com.example.travlediary.repository.inquiry.InquiryMapper;
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
 * 보존기간이 끝난 문의 정리 배치.
 * 회원 탈퇴와 무관한 독립 수명이라, 회원 상태는 여기에서 전혀 보지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class InquiryRetentionBatchServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 4, 40);
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2023, 9, 12, 4, 40);

    @Mock
    private InquiryMapper inquiryMapper;
    @Mock
    private InquiryRetentionTransactionService inquiryRetentionTransactionService;

    private InquiryRetentionBatchService batchService() {
        return new InquiryRetentionBatchService(
                inquiryMapper, inquiryRetentionTransactionService);
    }

    /** 만료 기준은 실행 시각에서 3년 전이고, 한 번에 batch 크기만큼만 읽는다. */
    @Test
    void theBatchAsksForInquiriesFinishedBeforeTheThreeYearCutoff() {
        when(inquiryMapper.findExpiredInquiryIds(CUTOFF, InquiryRetentionBatchService.BATCH_SIZE))
                .thenReturn(List.of(3L));
        when(inquiryRetentionTransactionService.purgeOne(3L, CUTOFF)).thenReturn(true);

        assertThat(batchService().purgeExpiredInquiries(NOW)).isEqualTo(1);

        verify(inquiryMapper).findExpiredInquiryIds(CUTOFF, 100);
        verify(inquiryRetentionTransactionService).purgeOne(3L, CUTOFF);
    }

    @Test
    void nothingToPurgeDoesNotOpenAnyTransaction() {
        when(inquiryMapper.findExpiredInquiryIds(CUTOFF, InquiryRetentionBatchService.BATCH_SIZE))
                .thenReturn(List.of());

        assertThat(batchService().purgeExpiredInquiries(NOW)).isZero();

        verifyNoInteractions(inquiryRetentionTransactionService);
    }

    /** 읽은 뒤 상태가 되돌아간 문의는 지우지 않고 건너뛴다. */
    @Test
    void anInquiryThatIsNoLongerEligibleIsSkipped() {
        when(inquiryMapper.findExpiredInquiryIds(CUTOFF, InquiryRetentionBatchService.BATCH_SIZE))
                .thenReturn(List.of(3L, 4L));
        when(inquiryRetentionTransactionService.purgeOne(3L, CUTOFF)).thenReturn(false);
        when(inquiryRetentionTransactionService.purgeOne(4L, CUTOFF)).thenReturn(true);

        assertThat(batchService().purgeExpiredInquiries(NOW)).isEqualTo(1);
    }

    /** 한 건이 실패해도 나머지는 계속 처리한다. 실패한 문의는 그대로 남아 다음 주기가 다시 집는다. */
    @Test
    void oneFailureDoesNotStopTheRestOfTheBatch() {
        when(inquiryMapper.findExpiredInquiryIds(CUTOFF, InquiryRetentionBatchService.BATCH_SIZE))
                .thenReturn(List.of(3L, 4L, 5L));
        when(inquiryRetentionTransactionService.purgeOne(3L, CUTOFF)).thenReturn(true);
        when(inquiryRetentionTransactionService.purgeOne(4L, CUTOFF))
                .thenThrow(new IllegalStateException("db down"));
        when(inquiryRetentionTransactionService.purgeOne(5L, CUTOFF)).thenReturn(true);

        assertThat(batchService().purgeExpiredInquiries(NOW)).isEqualTo(2);

        verify(inquiryRetentionTransactionService).purgeOne(5L, CUTOFF);
    }

    @Test
    void aMissingRunTimeDoesNothing() {
        assertThat(batchService().purgeExpiredInquiries(null)).isZero();

        verify(inquiryMapper, never()).findExpiredInquiryIds(any(), anyInt());
        verify(inquiryRetentionTransactionService, never()).purgeOne(anyLong(), any());
    }

    /** 스케줄러는 하루 한 번, 다른 배치와 겹치지 않는 새벽 시간에 돈다. */
    @Test
    void theSchedulerRunsOnceADayOutsideTheHourlyBatchSlots() throws Exception {
        String cron = InquiryRetentionScheduler.class
                .getDeclaredMethod("purgeExpiredInquiries")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class)
                .cron();

        assertThat(cron).isEqualTo("0 40 4 * * *");
    }
}
