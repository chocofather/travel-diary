package com.example.travlediary.service.user;

import com.example.travlediary.model.AccountPurgeTask;
import com.example.travlediary.model.AccountPurgeTaskType;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.repository.user.AccountPurgeMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * KAKAO SOCIAL_UNLINK task 를 실제로 처리한다.
 *
 * <p>파일 삭제 worker 와 같은 구조다. task 조회 → 짧은 트랜잭션으로 집어 들기 → (트랜잭션 밖)
 * 카카오 API 호출 → 짧은 트랜잭션으로 결과 기록 → job 완료 판정. 외부 호출 동안 DB 트랜잭션이나
 * row lock 을 쥐지 않는다.
 *
 * <p>조회와 claim 이 모두 {@code task_type='SOCIAL_UNLINK' AND provider='KAKAO'} 로 한정돼 있어
 * FILE_DELETE 나 다른 provider 의 task 는 집히지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AccountPurgeSocialUnlinkWorker {

    /** 파일 삭제 worker 와 같은 크기. 남은 task 는 다음 주기가 이어받는다. */
    static final int BATCH_SIZE = 100;

    /** 서버 배치만으로 연결 해제를 시도할 수 있는 provider. */
    private static final SocialProvider PROVIDER = SocialProvider.KAKAO;

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeSocialUnlinkWorker.class);

    private final AccountPurgeMapper accountPurgeMapper;
    private final AccountPurgeTaskTransactionService taskTransactionService;
    private final KakaoAdminUnlinkClient kakaoAdminUnlinkClient;

    /**
     * @return 이번 실행에서 완료 처리한 task 수
     */
    public int processReadyUnlinkTasks(LocalDateTime currentTime) {
        List<AccountPurgeTask> readyTasks = accountPurgeMapper.findReadyTasks(
                AccountPurgeTaskType.SOCIAL_UNLINK, PROVIDER, currentTime, BATCH_SIZE);
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
                log.error("Purge unlink task aborted: taskId={}, exceptionType={}",
                        readyTask.getId(), exception.getClass().getSimpleName(), exception);
            }
        }

        log.info("Purge unlink task batch completed: ready={}, completed={}, failed={}",
                readyTasks.size(), completed, failed);
        return completed;
    }

    /**
     * @return 이번에 완료 처리했으면 true. 남이 가져갔거나 실패면 false.
     */
    private boolean processOne(AccountPurgeTask readyTask, LocalDateTime currentTime) {
        AccountPurgeTask task = taskTransactionService.claim(
                readyTask.getId(), AccountPurgeTaskType.SOCIAL_UNLINK, PROVIDER, currentTime);
        if (task == null) {
            // 다른 worker 가 먼저 집었거나 이미 끝난 task 다.
            return false;
        }

        try {
            // 트랜잭션 밖에서 호출한다. 회원번호는 여기서만 쓰이고 로그로 나가지 않는다.
            kakaoAdminUnlinkClient.unlinkByUserId(task.getTargetValue());
        } catch (KakaoAdminUnlinkException exception) {
            recordUnlinkFailure(task, currentTime, exception);
            return false;
        }

        boolean jobCompleted = taskTransactionService.recordSuccess(task, currentTime);
        log.info("Purge unlink task completed: purgeJobId={}, taskId={}, jobCompleted={}",
                task.getPurgeJobId(), task.getId(), jobCompleted);
        return true;
    }

    /**
     * 다시 시도해서 달라질 수 있는 실패만 재시도한다.
     * 4xx 는 이미 해제된 회원인지 권한 문제인지 공식 오류코드 대조 없이는 구분할 수 없어
     * 성공으로 넘기지 않고 운영 점검 대상으로 남긴다.
     */
    private void recordUnlinkFailure(AccountPurgeTask task, LocalDateTime currentTime,
                                     KakaoAdminUnlinkException exception) {
        if (exception.isRetryable()) {
            taskTransactionService.recordFailure(task, currentTime, exception.errorCode());
            log.warn("Purge unlink failed, will retry: purgeJobId={}, taskId={}, attempts={}, kind={}",
                    task.getPurgeJobId(), task.getId(), task.getAttempts(), exception.getKind());
            return;
        }
        taskTransactionService.recordPermanentFailure(task, exception.errorCode());
        log.error("Purge unlink needs manual review: purgeJobId={}, taskId={}, kind={}",
                task.getPurgeJobId(), task.getId(), exception.getKind());
    }
}
