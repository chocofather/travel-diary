package com.example.travlediary.service.user;

import com.example.travlediary.repository.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 유예가 끝난 회원을 모아 한 명씩 최종 파기한다.
 *
 * <p>여기에는 트랜잭션을 걸지 않는다. 회원 한 명의 경계는
 * {@link AccountPurgeTransactionService#purgeOne}이 갖고, 이 클래스가 별도 빈을 거쳐 호출하므로
 * Spring 프록시를 정상적으로 통과한다(같은 클래스 안에서 부르면 @Transactional 이 무시된다).
 *
 * <p>한 회원의 실패는 그 회원의 트랜잭션만 되돌리고 다음 회원 처리를 막지 않는다.
 * 실패한 회원은 WITHDRAWAL_PENDING 그대로 남아 다음 실행에서 다시 선택된다.
 */
@Service
@RequiredArgsConstructor
public class AccountPurgeBatchService {

    /** 한 번 실행에서 처리할 최대 회원 수. 남은 회원은 다음 주기가 이어받는다. */
    static final int BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeBatchService.class);

    private final UserMapper userMapper;
    private final AccountPurgeTransactionService accountPurgeTransactionService;

    /**
     * @return 실제로 파기한 회원 수
     */
    public int purgeDueAccounts(LocalDateTime currentTime) {
        List<Long> dueUserIds = userMapper.findDueWithdrawalUserIds(currentTime, BATCH_SIZE);
        if (dueUserIds == null || dueUserIds.isEmpty()) {
            return 0;
        }

        int purged = 0;
        int failed = 0;
        for (Long userId : dueUserIds) {
            try {
                AccountPurgeTransactionService.PurgeOutcome outcome =
                        accountPurgeTransactionService.purgeOne(userId, currentTime);
                if (!outcome.purged()) {
                    // 복구됐거나 다른 실행이 먼저 처리한 회원이다.
                    continue;
                }
                purged++;
                log.info("Account purge finished: userId={}, purgeJobId={}, jobStatus={}, tasks={}",
                        userId, outcome.purgeJobId(), outcome.jobStatus(), outcome.taskCount());
            } catch (RuntimeException exception) {
                failed++;
                log.error("Account purge failed, the account stays pending: userId={}, exceptionType={}",
                        userId, exception.getClass().getSimpleName(), exception);
            }
        }

        log.info("Account purge batch completed: due={}, purged={}, failed={}",
                dueUserIds.size(), purged, failed);
        return purged;
    }
}
