package com.example.travlediary.service.user;

import com.example.travlediary.repository.user.UserPolicyConsentMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 보관기간이 끝난 탈퇴 회원의 동의 이력을 모아 회원 단위로 정리한다.
 * 문의 보존기간 배치({@code InquiryRetentionBatchService})와 같은 구조다.
 *
 * <p>여기에는 트랜잭션을 걸지 않는다. 회원 한 명의 경계는
 * {@link PolicyConsentRetentionTransactionService#purgeConsentsOf}가 갖고,
 * 이 클래스가 별도 빈을 거쳐 호출하므로 Spring 프록시를 정상적으로 통과한다.
 *
 * <p>한 명의 실패는 그 회원의 트랜잭션만 되돌리고 다음 회원 처리를 막지 않는다.
 * 실패한 회원은 그대로 남아 다음 실행에서 다시 선택된다.
 */
@Service
@RequiredArgsConstructor
public class PolicyConsentRetentionBatchService {

    /** 한 번 실행에서 처리할 최대 회원 수. 남은 회원은 다음 주기가 이어받는다. */
    static final int BATCH_SIZE = 100;

    private static final Logger log =
            LoggerFactory.getLogger(PolicyConsentRetentionBatchService.class);

    private final UserPolicyConsentMapper userPolicyConsentMapper;
    private final PolicyConsentRetentionTransactionService policyConsentRetentionTransactionService;

    /**
     * @param currentTime 보관기간 만료 판정 기준 시각
     * @return 동의 이력을 실제로 지운 회원 수
     */
    public int purgeExpiredConsents(LocalDateTime currentTime) {
        if (currentTime == null) {
            return 0;
        }

        LocalDateTime retentionCutoff =
                PolicyConsentRetentionPolicy.retentionCutoff(currentTime);
        List<Long> expiredUserIds = userPolicyConsentMapper
                .findConsentRetentionExpiredUserIds(retentionCutoff, BATCH_SIZE);
        if (expiredUserIds == null || expiredUserIds.isEmpty()) {
            return 0;
        }

        int purgedUsers = 0;
        int purgedRows = 0;
        int failed = 0;
        for (Long userId : expiredUserIds) {
            try {
                int deleted = policyConsentRetentionTransactionService
                        .purgeConsentsOf(userId, retentionCutoff);
                if (deleted > 0) {
                    purgedUsers++;
                    purgedRows += deleted;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.error("Policy consent retention purge failed, the consents stay: "
                                + "userId={}, exceptionType={}",
                        userId, exception.getClass().getSimpleName(), exception);
            }
        }

        log.info("Policy consent retention batch completed: due={}, purgedUsers={}, "
                        + "purgedRows={}, failed={}, retentionCutoff={}",
                expiredUserIds.size(), purgedUsers, purgedRows, failed, retentionCutoff);
        return purgedUsers;
    }
}
