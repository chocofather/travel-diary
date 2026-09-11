package com.example.travlediary.service.user;

import com.example.travlediary.model.AccountPurgeTask;
import com.example.travlediary.model.AccountPurgeTaskType;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.repository.user.AccountPurgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 후처리 task 한 건의 상태 변경. 파일 IO 전후로 짧게 열고 바로 닫는다.
 *
 * <p>파일 삭제는 되돌릴 수 없으므로 긴 트랜잭션 안에서 지우지 않는다. 지우기 전에 집어 들고,
 * 지운 뒤에 결과만 따로 기록한다. 파일은 지워졌는데 기록이 실패해도, 다음 실행에서
 * "이미 없음"으로 다시 성공 처리되므로 결과는 같아진다.
 */
@Service
@RequiredArgsConstructor
public class AccountPurgeTaskTransactionService {

    private final AccountPurgeMapper accountPurgeMapper;

    /**
     * 처리 권한을 얻고 시도 횟수를 올린다.
     *
     * @param taskType 이 worker 가 맡은 종류. 다른 종류의 task 는 집히지 않는다.
     * @param provider null 이면 provider 를 따지지 않는다 (FILE_DELETE 는 provider 가 없다).
     * @return 권한을 얻었으면 갱신된 task, 그 사이 남이 가져갔으면 null
     */
    @Transactional
    public AccountPurgeTask claim(Long taskId, AccountPurgeTaskType taskType,
                                  SocialProvider provider, LocalDateTime currentTime) {
        LocalDateTime leaseUntil = currentTime.plus(AccountPurgeRetryPolicy.CLAIM_LEASE);
        if (accountPurgeMapper.claimTask(taskId, taskType, provider, currentTime, leaseUntil) != 1) {
            return null;
        }
        // 시도 횟수는 방금 올라갔다. backoff 계산에 그 값을 그대로 쓴다.
        return accountPurgeMapper.findTaskById(taskId);
    }

    /**
     * 성공 기록. 같은 job 의 남은 task 가 없으면 job 도 함께 끝낸다.
     *
     * @return job 까지 완료 처리했으면 true
     */
    @Transactional
    public boolean recordSuccess(AccountPurgeTask task, LocalDateTime currentTime) {
        // 이미 다른 실행이 끝낸 task 라 0 행이어도 job 완료 판정은 해 볼 값어치가 있다.
        accountPurgeMapper.markTaskCompleted(task.getId(), currentTime);
        return accountPurgeMapper.completeJobIfAllTasksDone(
                task.getPurgeJobId(), currentTime) == 1;
    }

    /** 실패 기록. 시도 횟수를 다 쓰면 자동 재시도를 멈춘다. */
    @Transactional
    public void recordFailure(AccountPurgeTask task, LocalDateTime currentTime, String lastError) {
        if (AccountPurgeRetryPolicy.exhausted(task.getAttempts())) {
            accountPurgeMapper.markTaskFailed(task.getId(), lastError);
            return;
        }
        accountPurgeMapper.markTaskRetry(
                task.getId(),
                AccountPurgeRetryPolicy.nextRetryAt(currentTime, task.getAttempts()),
                lastError);
    }

    /** 다시 시도해도 결과가 같은 실패. 재시도하지 않고 바로 운영 점검 대상으로 둔다. */
    @Transactional
    public void recordPermanentFailure(AccountPurgeTask task, String lastError) {
        accountPurgeMapper.markTaskFailed(task.getId(), lastError);
    }
}
