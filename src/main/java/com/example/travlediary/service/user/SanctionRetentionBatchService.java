package com.example.travlediary.service.user;

import com.example.travlediary.repository.user.BlockedEmailMapper;
import com.example.travlediary.repository.user.UserSanctionMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 보관기간이 끝난 제재 이력과 재가입 방지 기록을 정리한다.
 * 문의/동의 이력 보존기간 배치와 같은 구조다.
 *
 * <p>두 정리는 기준일이 서로 다르므로 한 실행 안에서 각각 따로 돈다.
 * 다만 순서는 고정이다. 재가입 차단 기록을 먼저 지워야 최종 탈퇴 회원의 적용중 영구제재가
 * 같은 실행에서 정리될 수 있다. {@link SanctionRetentionPolicy} 참고.
 *
 * <p>여기에는 트랜잭션을 걸지 않는다. 대상 하나의 경계는
 * {@link SanctionRetentionTransactionService}가 갖고, 이 클래스가 별도 빈을 거쳐
 * 호출하므로 Spring 프록시를 정상적으로 통과한다.
 *
 * <p>하나의 실패는 그 대상의 트랜잭션만 되돌리고 나머지 처리를 막지 않는다.
 * 실패한 대상은 그대로 남아 다음 실행에서 다시 선택된다.
 *
 * <p>로그에는 대상 수와 id 만 남긴다. 이메일 원문도, 해시값도 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SanctionRetentionBatchService {

    /** 한 번 실행에서 처리할 최대 대상 수. 남은 대상은 다음 주기가 이어받는다. */
    static final int BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(SanctionRetentionBatchService.class);

    private final UserSanctionMapper userSanctionMapper;
    private final BlockedEmailMapper blockedEmailMapper;
    private final SanctionRetentionTransactionService sanctionRetentionTransactionService;

    /**
     * @param currentTime 보관기간 만료 판정 기준 시각
     * @return 지운 제재 건수와 차단 기록을 지운 회원 수의 합
     */
    public int purgeExpiredSanctionRecords(LocalDateTime currentTime) {
        if (currentTime == null) {
            return 0;
        }
        LocalDateTime retentionCutoff = SanctionRetentionPolicy.retentionCutoff(currentTime);
        // 차단 기록을 먼저 지운다. 최종 탈퇴 회원의 적용중 영구제재는 그 회원의 차단 기록이
        // 남아 있는 동안 대상에서 빠지므로, 이 순서라야 같은 실행 안에서 함께 정리된다.
        int purgedBlocks = purgeReSignupBlocksOfPurgedUsers(retentionCutoff);
        return purgedBlocks + purgeEndedSanctions(retentionCutoff);
    }

    /**
     * 보관기간이 끝난 제재 이력. 종료된 제재는 종료 후 3년, 적용중인 영구제재는 회원이
     * 최종 탈퇴한 뒤 3년이다. 계정이 살아 있는 회원의 적용중 제재는 대상에 들어오지 않는다.
     */
    private int purgeEndedSanctions(LocalDateTime retentionCutoff) {
        List<Long> sanctionIds =
                userSanctionMapper.findRetentionExpiredSanctionIds(retentionCutoff, BATCH_SIZE);
        if (sanctionIds == null || sanctionIds.isEmpty()) {
            return 0;
        }

        int purged = 0;
        int failed = 0;
        for (Long sanctionId : sanctionIds) {
            try {
                if (sanctionRetentionTransactionService.purgeSanction(sanctionId, retentionCutoff)) {
                    purged++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.error("Sanction retention purge failed, the record stays: "
                                + "sanctionId={}, exceptionType={}",
                        sanctionId, exception.getClass().getSimpleName(), exception);
            }
        }

        log.info("Sanction history retention completed: due={}, purged={}, failed={}, "
                        + "retentionCutoff={}",
                sanctionIds.size(), purged, failed, retentionCutoff);
        return purged;
    }

    /** 최종 탈퇴 후 3년이 지난 회원의 재가입 차단 기록. 기준일이 제재 종료일과 다르다. */
    private int purgeReSignupBlocksOfPurgedUsers(LocalDateTime retentionCutoff) {
        List<Long> userIds = blockedEmailMapper
                .findReSignupBlockRetentionExpiredUserIds(retentionCutoff, BATCH_SIZE);
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }

        int purgedUsers = 0;
        int purgedRows = 0;
        int failed = 0;
        for (Long userId : userIds) {
            try {
                int deleted = sanctionRetentionTransactionService
                        .purgeReSignupBlocksOf(userId, retentionCutoff);
                if (deleted > 0) {
                    purgedUsers++;
                    purgedRows += deleted;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.error("Re-signup block retention purge failed, the record stays: "
                                + "userId={}, exceptionType={}",
                        userId, exception.getClass().getSimpleName(), exception);
            }
        }

        log.info("Re-signup block retention completed: due={}, purgedUsers={}, purgedRows={}, "
                        + "failed={}, retentionCutoff={}",
                userIds.size(), purgedUsers, purgedRows, failed, retentionCutoff);
        return purgedUsers;
    }
}
