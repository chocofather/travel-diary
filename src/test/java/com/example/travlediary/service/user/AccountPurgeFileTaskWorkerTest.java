package com.example.travlediary.service.user;

import com.example.travlediary.model.AccountPurgeTask;
import com.example.travlediary.model.AccountPurgeTaskStatus;
import com.example.travlediary.model.AccountPurgeTaskType;
import com.example.travlediary.repository.user.AccountPurgeMapper;
import com.example.travlediary.service.file.ManagedUploadFileDeleter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountPurgeFileTaskWorkerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 4, 10);

    @Mock private AccountPurgeMapper accountPurgeMapper;
    @Mock private AccountPurgeTaskTransactionService taskTransactionService;
    @Mock private ManagedUploadFileDeleter managedUploadFileDeleter;

    private AccountPurgeFileTaskWorker worker;

    @BeforeEach
    void setUp() {
        worker = new AccountPurgeFileTaskWorker(
                accountPurgeMapper, taskTransactionService, managedUploadFileDeleter);
    }

    /** 전체 task 를 한 번에 읽지 않는다. */
    @Test
    void theWorkerAsksForAtMostOnePageOfReadyTasks() {
        when(accountPurgeMapper.findReadyTasks(AccountPurgeTaskType.FILE_DELETE, null, NOW,
                AccountPurgeFileTaskWorker.BATCH_SIZE)).thenReturn(List.of());

        worker.processReadyFileDeleteTasks(NOW);

        verify(accountPurgeMapper).findReadyTasks(
                AccountPurgeTaskType.FILE_DELETE, null, NOW, 100);
        verifyNoInteractions(taskTransactionService, managedUploadFileDeleter);
    }

    @Test
    void aDeletedFileCompletesTheTask() throws IOException {
        AccountPurgeTask task = task(1L, "/uploads/diary-pages/a.jpg", 1);
        ready(task);
        when(taskTransactionService.claim(1L, AccountPurgeTaskType.FILE_DELETE, null, NOW)).thenReturn(task);
        when(managedUploadFileDeleter.delete("/uploads/diary-pages/a.jpg"))
                .thenReturn(ManagedUploadFileDeleter.DeletionOutcome.DELETED);

        assertThat(worker.processReadyFileDeleteTasks(NOW)).isEqualTo(1);

        verify(taskTransactionService).recordSuccess(task, NOW);
        verify(taskTransactionService, never()).recordFailure(any(), any(), anyString());
    }

    /**
     * 파일은 지워졌는데 상태 기록이 실패한 다음 실행. 파일이 없으니 성공으로 회복된다.
     */
    @Test
    void anAlreadyMissingFileStillCompletesTheTask() throws IOException {
        AccountPurgeTask task = task(1L, "/uploads/profiles/p.jpg", 2);
        ready(task);
        when(taskTransactionService.claim(1L, AccountPurgeTaskType.FILE_DELETE, null, NOW)).thenReturn(task);
        when(managedUploadFileDeleter.delete("/uploads/profiles/p.jpg"))
                .thenReturn(ManagedUploadFileDeleter.DeletionOutcome.ALREADY_ABSENT);

        assertThat(worker.processReadyFileDeleteTasks(NOW)).isEqualTo(1);

        verify(taskTransactionService).recordSuccess(task, NOW);
    }

    @Test
    void aFailedDeletionIsRecordedForRetry() throws IOException {
        AccountPurgeTask task = task(1L, "/uploads/diary-covers/c.jpg", 1);
        ready(task);
        when(taskTransactionService.claim(1L, AccountPurgeTaskType.FILE_DELETE, null, NOW)).thenReturn(task);
        when(managedUploadFileDeleter.delete(anyString())).thenThrow(new IOException("permission"));

        assertThat(worker.processReadyFileDeleteTasks(NOW)).isZero();

        verify(taskTransactionService).recordFailure(eq(task), eq(NOW),
                org.mockito.ArgumentMatchers.contains("IOException"));
        verify(taskTransactionService, never()).recordSuccess(any(), any());
    }

    /** last_error 에 경로나 원본 예외 메시지를 그대로 넣지 않는다. */
    @Test
    void theRecordedErrorDoesNotLeakThePathOrTheRawMessage() throws IOException {
        AccountPurgeTask task = task(1L, "/uploads/diary-covers/secret-name.jpg", 1);
        ready(task);
        when(taskTransactionService.claim(1L, AccountPurgeTaskType.FILE_DELETE, null, NOW)).thenReturn(task);
        when(managedUploadFileDeleter.delete(anyString()))
                .thenThrow(new IOException("/var/private/secret-name.jpg permission denied"));

        worker.processReadyFileDeleteTasks(NOW);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(taskTransactionService).recordFailure(any(), any(), captor.capture());
        assertThat(captor.getValue())
                .contains("IOException")
                .doesNotContain("secret-name.jpg", "/var/private", "permission denied");
    }

    /** DB 의 target 이 바뀌어 있어도 파일시스템을 건드리지 않는다. */
    @Test
    void aTamperedTargetIsNeverDeletedAndIsNotRetried() throws IOException {
        AccountPurgeTask task = task(1L, "/uploads/posts/public.jpg", 1);
        ready(task);
        when(taskTransactionService.claim(1L, AccountPurgeTaskType.FILE_DELETE, null, NOW)).thenReturn(task);

        assertThat(worker.processReadyFileDeleteTasks(NOW)).isZero();

        verifyNoInteractions(managedUploadFileDeleter);
        verify(taskTransactionService).recordPermanentFailure(eq(task),
                org.mockito.ArgumentMatchers.contains("REJECTED_TARGET"));
        verify(taskTransactionService, never()).recordFailure(any(), any(), anyString());
    }

    @Test
    void aStickerPathIsRejectedBeforeTouchingTheFilesystem() throws IOException {
        AccountPurgeTask task = task(1L, "/images/diary/stickers/travel/airplane.svg", 1);
        ready(task);
        when(taskTransactionService.claim(1L, AccountPurgeTaskType.FILE_DELETE, null, NOW)).thenReturn(task);

        worker.processReadyFileDeleteTasks(NOW);

        verifyNoInteractions(managedUploadFileDeleter);
        verify(taskTransactionService).recordPermanentFailure(eq(task), anyString());
    }

    /** 업로드 루트 밖으로 계산되는 경로는 다시 시도해도 같으므로 재시도하지 않는다. */
    @Test
    void aTargetResolvedOutsideTheUploadRootIsPermanentlyFailed() throws IOException {
        AccountPurgeTask task = task(1L, "/uploads/diary-pages/a.jpg", 1);
        ready(task);
        when(taskTransactionService.claim(1L, AccountPurgeTaskType.FILE_DELETE, null, NOW)).thenReturn(task);
        when(managedUploadFileDeleter.delete(anyString()))
                .thenReturn(ManagedUploadFileDeleter.DeletionOutcome.REJECTED);

        worker.processReadyFileDeleteTasks(NOW);

        verify(taskTransactionService).recordPermanentFailure(eq(task), anyString());
        verify(taskTransactionService, never()).recordFailure(any(), any(), anyString());
    }

    /** 다른 worker 가 먼저 집어간 task 는 건너뛴다. */
    @Test
    void aTaskClaimedElsewhereIsSkippedWithoutTouchingTheFilesystem() throws IOException {
        ready(task(1L, "/uploads/diary-pages/a.jpg", 1));
        when(taskTransactionService.claim(1L, AccountPurgeTaskType.FILE_DELETE, null, NOW)).thenReturn(null);

        assertThat(worker.processReadyFileDeleteTasks(NOW)).isZero();

        verifyNoInteractions(managedUploadFileDeleter);
        verify(taskTransactionService, never()).recordSuccess(any(), any());
    }

    @Test
    void oneFailingTaskDoesNotStopTheRest() throws IOException {
        AccountPurgeTask first = task(1L, "/uploads/diary-pages/a.jpg", 1);
        AccountPurgeTask second = task(2L, "/uploads/diary-pages/b.jpg", 1);
        AccountPurgeTask third = task(3L, "/uploads/diary-pages/c.jpg", 1);
        ready(first, second, third);
        when(taskTransactionService.claim(1L, AccountPurgeTaskType.FILE_DELETE, null, NOW)).thenReturn(first);
        when(taskTransactionService.claim(2L, AccountPurgeTaskType.FILE_DELETE, null, NOW))
                .thenThrow(new IllegalStateException("boom"));
        when(taskTransactionService.claim(3L, AccountPurgeTaskType.FILE_DELETE, null, NOW))
                .thenReturn(third);
        when(managedUploadFileDeleter.delete(anyString()))
                .thenReturn(ManagedUploadFileDeleter.DeletionOutcome.DELETED);

        assertThat(worker.processReadyFileDeleteTasks(NOW)).isEqualTo(2);

        verify(taskTransactionService).recordSuccess(third, NOW);
    }

    /** SOCIAL_UNLINK 는 조회 단계에서 빠지므로 worker 가 실행할 길이 없다. */
    @Test
    void theWorkerOnlyEverAsksForFileDeleteTasks() {
        when(accountPurgeMapper.findReadyTasks(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        worker.processReadyFileDeleteTasks(NOW);

        verify(accountPurgeMapper).findReadyTasks(
                AccountPurgeTaskType.FILE_DELETE, null, NOW, 100);
        verify(accountPurgeMapper, never()).findReadyTasks(
                eq(AccountPurgeTaskType.SOCIAL_UNLINK), any(), any(), anyInt());
    }

    /** 배치 루프는 트랜잭션을 열지 않는다. 경계는 task 별 서비스가 갖는다. */
    @Test
    void theBatchLoopIsNotTransactional() throws Exception {
        assertThat(AccountPurgeFileTaskWorker.class.getAnnotation(
                org.springframework.transaction.annotation.Transactional.class)).isNull();
        assertThat(AccountPurgeFileTaskWorker.class
                .getMethod("processReadyFileDeleteTasks", LocalDateTime.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNull();
    }

    private void ready(AccountPurgeTask... tasks) {
        when(accountPurgeMapper.findReadyTasks(
                eq(AccountPurgeTaskType.FILE_DELETE), eq(null), eq(NOW), anyInt()))
                .thenReturn(List.of(tasks));
    }

    private AccountPurgeTask task(Long id, String targetValue, int attempts) {
        AccountPurgeTask task = new AccountPurgeTask();
        task.setId(id);
        task.setPurgeJobId(100L);
        task.setTaskType(AccountPurgeTaskType.FILE_DELETE);
        task.setTargetValue(targetValue);
        task.setStatus(AccountPurgeTaskStatus.PENDING);
        task.setAttempts(attempts);
        return task;
    }
}
