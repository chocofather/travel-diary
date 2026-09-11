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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountPurgeSocialUnlinkWorkerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 4, 20);

    @Mock private AccountPurgeMapper accountPurgeMapper;
    @Mock private AccountPurgeTaskTransactionService taskTransactionService;
    @Mock private KakaoAdminUnlinkClient kakaoAdminUnlinkClient;

    private AccountPurgeSocialUnlinkWorker worker;

    @BeforeEach
    void setUp() {
        worker = new AccountPurgeSocialUnlinkWorker(
                accountPurgeMapper, taskTransactionService, kakaoAdminUnlinkClient);
    }

    /** 전체 task 를 한 번에 읽지 않고, KAKAO SOCIAL_UNLINK 만 묻는다. */
    @Test
    void onlyKakaoUnlinkTasksArePickedUpOnePageAtATime() {
        when(accountPurgeMapper.findReadyTasks(AccountPurgeTaskType.SOCIAL_UNLINK,
                SocialProvider.KAKAO, NOW, AccountPurgeSocialUnlinkWorker.BATCH_SIZE))
                .thenReturn(List.of());

        worker.processReadyUnlinkTasks(NOW);

        verify(accountPurgeMapper).findReadyTasks(
                AccountPurgeTaskType.SOCIAL_UNLINK, SocialProvider.KAKAO, NOW, 100);
        verify(accountPurgeMapper, never()).findReadyTasks(
                eq(AccountPurgeTaskType.FILE_DELETE), any(), any(), anyInt());
        verifyNoInteractions(taskTransactionService, kakaoAdminUnlinkClient);
    }

    /** claim 에도 종류와 provider 조건이 걸려 FILE_DELETE 는 집히지 않는다. */
    @Test
    void theClaimIsScopedToKakaoUnlinkTasks() {
        AccountPurgeTask task = task(1L, "123456789");
        ready(task);
        when(taskTransactionService.claim(1L, AccountPurgeTaskType.SOCIAL_UNLINK,
                SocialProvider.KAKAO, NOW)).thenReturn(task);

        worker.processReadyUnlinkTasks(NOW);

        verify(taskTransactionService).claim(
                1L, AccountPurgeTaskType.SOCIAL_UNLINK, SocialProvider.KAKAO, NOW);
        verify(taskTransactionService, never()).claim(
                any(), eq(AccountPurgeTaskType.FILE_DELETE), any(), any());
    }

    @Test
    void aSuccessfulUnlinkCompletesTheTask() {
        AccountPurgeTask task = claimable(1L, "123456789");

        assertThat(worker.processReadyUnlinkTasks(NOW)).isEqualTo(1);

        verify(kakaoAdminUnlinkClient).unlinkByUserId("123456789");
        verify(taskTransactionService).recordSuccess(task, NOW);
        verify(taskTransactionService, never()).recordFailure(any(), any(), anyString());
        verify(taskTransactionService, never()).recordPermanentFailure(any(), anyString());
    }

    /** 다른 worker 가 먼저 집었으면 카카오 API 를 부르지 않는다. */
    @Test
    void aTaskClaimedElsewhereNeverReachesTheApi() {
        ready(task(1L, "123456789"));
        when(taskTransactionService.claim(any(), any(), any(), any())).thenReturn(null);

        assertThat(worker.processReadyUnlinkTasks(NOW)).isZero();

        verifyNoInteractions(kakaoAdminUnlinkClient);
    }

    @ParameterizedTest
    @EnumSource(value = KakaoAdminUnlinkException.Kind.class,
            names = {"CONFIGURATION", "TIMEOUT", "HTTP_5XX"})
    void transientFailuresAreRetried(KakaoAdminUnlinkException.Kind kind) {
        AccountPurgeTask task = claimable(1L, "123456789");
        doThrow(new KakaoAdminUnlinkException(kind))
                .when(kakaoAdminUnlinkClient).unlinkByUserId(anyString());

        assertThat(worker.processReadyUnlinkTasks(NOW)).isZero();

        verify(taskTransactionService).recordFailure(eq(task), eq(NOW),
                org.mockito.ArgumentMatchers.contains(kind.name()));
        verify(taskTransactionService, never()).recordPermanentFailure(any(), anyString());
    }

    /**
     * 4xx 와 응답 불일치는 성공으로 넘기지 않고 운영 확인 대상으로 남긴다.
     * 잘못된 회원번호는 API 호출 전에 걸러진다.
     */
    @ParameterizedTest
    @EnumSource(value = KakaoAdminUnlinkException.Kind.class,
            names = {"HTTP_4XX", "RESPONSE_MISMATCH", "INVALID_PROVIDER_USER_ID"})
    void permanentFailuresAreNotRetriedAndNeverCountAsSuccess(
            KakaoAdminUnlinkException.Kind kind) {
        AccountPurgeTask task = claimable(1L, "123456789");
        doThrow(new KakaoAdminUnlinkException(kind))
                .when(kakaoAdminUnlinkClient).unlinkByUserId(anyString());

        assertThat(worker.processReadyUnlinkTasks(NOW)).isZero();

        verify(taskTransactionService).recordPermanentFailure(eq(task),
                org.mockito.ArgumentMatchers.contains(kind.name()));
        verify(taskTransactionService, never()).recordFailure(any(), any(), anyString());
        verify(taskTransactionService, never()).recordSuccess(any(), any());
    }

    /** last_error 에 회원번호가 섞이지 않는다. */
    @Test
    void theRecordedErrorNeverCarriesTheProviderUserId() {
        claimable(1L, "987654321");
        doThrow(new KakaoAdminUnlinkException(KakaoAdminUnlinkException.Kind.HTTP_5XX))
                .when(kakaoAdminUnlinkClient).unlinkByUserId(anyString());

        worker.processReadyUnlinkTasks(NOW);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(taskTransactionService).recordFailure(any(), any(), captor.capture());
        assertThat(captor.getValue()).contains("HTTP_5XX").doesNotContain("987654321");
    }

    @Test
    void oneFailingTaskDoesNotStopTheRest() {
        AccountPurgeTask first = task(1L, "111");
        AccountPurgeTask second = task(2L, "222");
        AccountPurgeTask third = task(3L, "333");
        ready(first, second, third);
        when(taskTransactionService.claim(1L, AccountPurgeTaskType.SOCIAL_UNLINK,
                SocialProvider.KAKAO, NOW)).thenReturn(first);
        when(taskTransactionService.claim(2L, AccountPurgeTaskType.SOCIAL_UNLINK,
                SocialProvider.KAKAO, NOW)).thenThrow(new IllegalStateException("boom"));
        when(taskTransactionService.claim(3L, AccountPurgeTaskType.SOCIAL_UNLINK,
                SocialProvider.KAKAO, NOW)).thenReturn(third);

        assertThat(worker.processReadyUnlinkTasks(NOW)).isEqualTo(2);

        verify(taskTransactionService).recordSuccess(third, NOW);
    }

    /** job 완료 판정은 공용 경로(recordSuccess)가 맡는다. */
    @Test
    void theJobIsClosedThroughTheSharedCompletionPath() {
        AccountPurgeTask task = claimable(1L, "123456789");
        when(taskTransactionService.recordSuccess(task, NOW)).thenReturn(true);

        assertThat(worker.processReadyUnlinkTasks(NOW)).isEqualTo(1);

        verify(taskTransactionService).recordSuccess(task, NOW);
    }

    /** 배치 루프는 트랜잭션을 열지 않는다. 외부 호출이 트랜잭션 안에 들어가지 않게 한다. */
    @Test
    void theBatchLoopIsNotTransactional() throws Exception {
        assertThat(AccountPurgeSocialUnlinkWorker.class.getAnnotation(
                org.springframework.transaction.annotation.Transactional.class)).isNull();
        assertThat(AccountPurgeSocialUnlinkWorker.class
                .getMethod("processReadyUnlinkTasks", LocalDateTime.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNull();
    }

    private AccountPurgeTask claimable(Long id, String providerUserId) {
        AccountPurgeTask task = task(id, providerUserId);
        ready(task);
        when(taskTransactionService.claim(id, AccountPurgeTaskType.SOCIAL_UNLINK,
                SocialProvider.KAKAO, NOW)).thenReturn(task);
        return task;
    }

    private void ready(AccountPurgeTask... tasks) {
        when(accountPurgeMapper.findReadyTasks(eq(AccountPurgeTaskType.SOCIAL_UNLINK),
                eq(SocialProvider.KAKAO), eq(NOW), anyInt())).thenReturn(List.of(tasks));
    }

    private AccountPurgeTask task(Long id, String providerUserId) {
        AccountPurgeTask task = new AccountPurgeTask();
        task.setId(id);
        task.setPurgeJobId(100L);
        task.setTaskType(AccountPurgeTaskType.SOCIAL_UNLINK);
        task.setProvider(SocialProvider.KAKAO);
        task.setTargetValue(providerUserId);
        task.setStatus(AccountPurgeTaskStatus.PENDING);
        task.setAttempts(1);
        return task;
    }
}
