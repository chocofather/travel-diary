package com.example.travlediary.service.user;

import com.example.travlediary.model.AccountPurgeTask;
import com.example.travlediary.model.AccountPurgeTaskStatus;
import com.example.travlediary.model.AccountPurgeTaskType;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.repository.user.AccountPurgeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountPurgeTaskTransactionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 4, 10);

    @Mock private AccountPurgeMapper accountPurgeMapper;

    private AccountPurgeTaskTransactionService service;

    @BeforeEach
    void setUp() {
        service = new AccountPurgeTaskTransactionService(accountPurgeMapper);
    }

    @Test
    void everyStateChangeIsItsOwnTransaction() throws Exception {
        for (Method method : new Method[]{
                AccountPurgeTaskTransactionService.class.getMethod(
                        "claim", Long.class, AccountPurgeTaskType.class, SocialProvider.class,
                        LocalDateTime.class),
                AccountPurgeTaskTransactionService.class.getMethod(
                        "recordSuccess", AccountPurgeTask.class, LocalDateTime.class),
                AccountPurgeTaskTransactionService.class.getMethod(
                        "recordFailure", AccountPurgeTask.class, LocalDateTime.class, String.class),
                AccountPurgeTaskTransactionService.class.getMethod(
                        "recordPermanentFailure", AccountPurgeTask.class, String.class)}) {
            assertThat(method.getAnnotation(
                    org.springframework.transaction.annotation.Transactional.class))
                    .as(method.getName()).isNotNull();
        }
    }

    /** 집어 들 때 임대 시각을 걸어 다른 worker 가 곧바로 같은 task 를 집지 못하게 한다. */
    @Test
    void claimingLeasesTheTaskAndReturnsTheUpdatedRow() {
        AccountPurgeTask claimed = task(2);
        when(accountPurgeMapper.claimTask(1L, AccountPurgeTaskType.FILE_DELETE, null, NOW,
                NOW.plus(AccountPurgeRetryPolicy.CLAIM_LEASE))).thenReturn(1);
        when(accountPurgeMapper.findTaskById(1L)).thenReturn(claimed);

        assertThat(service.claim(1L, AccountPurgeTaskType.FILE_DELETE, null, NOW))
                .isSameAs(claimed);
    }

    /** worker 마다 자기 종류만 집는다. 조건은 SQL 로 내려간다. */
    @Test
    void theClaimCarriesTheTaskTypeAndProviderOfTheCallingWorker() {
        when(accountPurgeMapper.claimTask(anyLong(), any(), any(), any(), any())).thenReturn(1);
        when(accountPurgeMapper.findTaskById(1L)).thenReturn(task(1));

        service.claim(1L, AccountPurgeTaskType.SOCIAL_UNLINK, SocialProvider.KAKAO, NOW);

        verify(accountPurgeMapper).claimTask(1L, AccountPurgeTaskType.SOCIAL_UNLINK,
                SocialProvider.KAKAO, NOW, NOW.plus(AccountPurgeRetryPolicy.CLAIM_LEASE));
    }

    @Test
    void aTaskClaimedByAnotherWorkerReturnsNull() {
        when(accountPurgeMapper.claimTask(anyLong(), any(), any(), any(), any())).thenReturn(0);

        assertThat(service.claim(1L, AccountPurgeTaskType.FILE_DELETE, null, NOW)).isNull();
        verify(accountPurgeMapper, never()).findTaskById(anyLong());
    }

    @Test
    void successMarksTheTaskAndTriesToCloseTheJob() {
        AccountPurgeTask task = task(1);
        when(accountPurgeMapper.markTaskCompleted(1L, NOW)).thenReturn(1);
        when(accountPurgeMapper.completeJobIfAllTasksDone(100L, NOW)).thenReturn(1);

        assertThat(service.recordSuccess(task, NOW)).isTrue();

        verify(accountPurgeMapper).markTaskCompleted(1L, NOW);
        verify(accountPurgeMapper).completeJobIfAllTasksDone(100L, NOW);
    }

    /** SOCIAL_UNLINK 가 남아 있으면 조건부 UPDATE 가 0 행이라 job 은 DB_DONE 그대로다. */
    @Test
    void theJobStaysOpenWhileAnyTaskIsUnfinished() {
        when(accountPurgeMapper.markTaskCompleted(1L, NOW)).thenReturn(1);
        when(accountPurgeMapper.completeJobIfAllTasksDone(100L, NOW)).thenReturn(0);

        assertThat(service.recordSuccess(task(1), NOW)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "1, 2026-09-11T04:15",
            "2, 2026-09-11T04:40",
            "3, 2026-09-11T06:10",
            "4, 2026-09-11T16:10",
            "5, 2026-09-12T04:10",
            "9, 2026-09-12T04:10"
    })
    void retriesBackOffInFixedSteps(int attempts, LocalDateTime expectedNextRetryAt) {
        service.recordFailure(task(attempts), NOW, "IOException: 파일을 삭제하지 못했습니다.");

        verify(accountPurgeMapper).markTaskRetry(
                1L, expectedNextRetryAt, "IOException: 파일을 삭제하지 못했습니다.");
        verify(accountPurgeMapper, never()).markTaskFailed(anyLong(), anyString());
    }

    /** 시도 횟수를 다 쓰면 자동 재시도를 멈추고 운영 점검 대상으로 남긴다. */
    @Test
    void anExhaustedTaskIsMarkedFailedInsteadOfRetried() {
        service.recordFailure(task(AccountPurgeRetryPolicy.MAX_ATTEMPTS), NOW, "IOException: x");

        verify(accountPurgeMapper).markTaskFailed(1L, "IOException: x");
        verify(accountPurgeMapper, never()).markTaskRetry(anyLong(), any(), anyString());
    }

    @Test
    void aPermanentFailureSkipsTheRetrySchedule() {
        service.recordPermanentFailure(task(1), "REJECTED_TARGET: 허용되지 않은 파일 경로입니다.");

        verify(accountPurgeMapper).markTaskFailed(1L, "REJECTED_TARGET: 허용되지 않은 파일 경로입니다.");
        verify(accountPurgeMapper, never()).markTaskRetry(anyLong(), any(), anyString());
    }

    /** FAILED 가 남아 있는 job 은 완료로 넘기지 않는다(조건부 UPDATE 가 막는다). */
    @Test
    void aFailedTaskNeverClosesTheJob() {
        when(accountPurgeMapper.completeJobIfAllTasksDone(anyLong(), any())).thenReturn(0);

        service.recordPermanentFailure(task(1), "REJECTED_TARGET");

        verify(accountPurgeMapper, never()).completeJobIfAllTasksDone(anyLong(), any());
    }

    private AccountPurgeTask task(int attempts) {
        AccountPurgeTask task = new AccountPurgeTask();
        task.setId(1L);
        task.setPurgeJobId(100L);
        task.setTaskType(AccountPurgeTaskType.FILE_DELETE);
        task.setTargetValue("/uploads/diary-pages/a.jpg");
        task.setStatus(AccountPurgeTaskStatus.PENDING);
        task.setAttempts(attempts);
        return task;
    }
}
