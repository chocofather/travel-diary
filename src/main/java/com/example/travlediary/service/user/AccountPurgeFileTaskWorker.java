package com.example.travlediary.service.user;

import com.example.travlediary.model.AccountPurgeTask;
import com.example.travlediary.model.AccountPurgeTaskType;
import com.example.travlediary.repository.user.AccountPurgeMapper;
import com.example.travlediary.service.file.ManagedUploadFileDeleter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FILE_DELETE task 를 실제로 처리한다.
 *
 * <p>흐름은 task 조회 → 경로 재검증 → 파일 삭제 → 짧은 트랜잭션으로 결과 기록 → job 완료 판정이다.
 * 여기에는 트랜잭션을 걸지 않는다. 경계는 {@link AccountPurgeTaskTransactionService}가 갖고
 * 별도 빈을 거쳐 부르므로 Spring 프록시를 정상적으로 지난다.
 *
 * <p>SOCIAL_UNLINK task 는 이 worker 가 건드리지 않는다. 조회 자체가 FILE_DELETE 만 본다.
 */
@Service
@RequiredArgsConstructor
public class AccountPurgeFileTaskWorker {

    /** 한 번 실행에서 처리할 최대 task 수. 남은 task 는 다음 주기가 이어받는다. */
    static final int BATCH_SIZE = 100;

    /** last_error 에 남기는 사유. 경로나 개인정보는 넣지 않는다. */
    private static final String REJECTED_TARGET_ERROR =
            "REJECTED_TARGET: 허용되지 않은 파일 경로입니다.";
    private static final String DELETE_FAILED_ERROR = "파일을 삭제하지 못했습니다.";

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeFileTaskWorker.class);

    private final AccountPurgeMapper accountPurgeMapper;
    private final AccountPurgeTaskTransactionService taskTransactionService;
    private final ManagedUploadFileDeleter managedUploadFileDeleter;

    /**
     * @return 이번 실행에서 완료 처리한 task 수
     */
    public int processReadyFileDeleteTasks(LocalDateTime currentTime) {
        List<AccountPurgeTask> readyTasks = accountPurgeMapper.findReadyTasks(
                AccountPurgeTaskType.FILE_DELETE, null, currentTime, BATCH_SIZE);
        if (readyTasks == null || readyTasks.isEmpty()) {
            return 0;
        }

        int completed = 0;
        int failed = 0;
        for (AccountPurgeTask readyTask : readyTasks) {
            try {
                if (processOne(readyTask, currentTime)) {
                    completed++;
                } else {
                    failed++;
                }
            } catch (RuntimeException exception) {
                // 한 task 의 예기치 못한 실패가 나머지 처리를 막지 않는다.
                failed++;
                log.error("Purge file task aborted: taskId={}, exceptionType={}",
                        readyTask.getId(), exception.getClass().getSimpleName(), exception);
            }
        }

        log.info("Purge file task batch completed: ready={}, completed={}, failed={}",
                readyTasks.size(), completed, failed);
        return completed;
    }

    /**
     * @return 이번에 완료 처리했으면 true. 남이 가져갔거나 실패면 false.
     */
    private boolean processOne(AccountPurgeTask readyTask, LocalDateTime currentTime) {
        AccountPurgeTask task = taskTransactionService.claim(
                readyTask.getId(), AccountPurgeTaskType.FILE_DELETE, null, currentTime);
        if (task == null) {
            // 다른 worker 가 먼저 집었거나 이미 끝난 task 다.
            return false;
        }

        // task 를 만들 때 걸렀더라도 실행 직전에 한 번 더 본다.
        // DB 값이 바뀌어 있어도 허용 폴더 밖 파일은 지우지 않는다.
        String targetValue = AccountPurgeFileTargets.normalizeDeletable(task.getTargetValue());
        if (targetValue == null) {
            log.error("Purge file task rejected by the target allowlist: purgeJobId={}, taskId={}",
                    task.getPurgeJobId(), task.getId());
            taskTransactionService.recordPermanentFailure(task, REJECTED_TARGET_ERROR);
            return false;
        }

        ManagedUploadFileDeleter.DeletionOutcome outcome;
        try {
            outcome = managedUploadFileDeleter.delete(targetValue);
        } catch (IOException | RuntimeException exception) {
            String lastError = summarize(exception);
            taskTransactionService.recordFailure(task, currentTime, lastError);
            log.warn("Purge file deletion failed: purgeJobId={}, taskId={}, attempts={}, exceptionType={}",
                    task.getPurgeJobId(), task.getId(), task.getAttempts(),
                    exception.getClass().getSimpleName());
            return false;
        }

        if (outcome == ManagedUploadFileDeleter.DeletionOutcome.REJECTED) {
            // 업로드 루트 밖으로 계산되는 경로. 다시 시도해도 결과가 같다.
            log.error("Purge file task target resolved outside the upload root: purgeJobId={}, taskId={}",
                    task.getPurgeJobId(), task.getId());
            taskTransactionService.recordPermanentFailure(task, REJECTED_TARGET_ERROR);
            return false;
        }

        // 이미 없는 파일도 목표 상태가 이뤄진 것이라 성공으로 본다.
        boolean jobCompleted = taskTransactionService.recordSuccess(task, currentTime);
        log.info("Purge file task completed: purgeJobId={}, taskId={}, outcome={}, jobCompleted={}",
                task.getPurgeJobId(), task.getId(), outcome, jobCompleted);
        return true;
    }

    /** 예외 타입과 짧은 사유만 남긴다. 경로와 stacktrace 는 넣지 않는다. */
    private String summarize(Exception exception) {
        return exception.getClass().getSimpleName() + ": " + DELETE_FAILED_ERROR;
    }
}
