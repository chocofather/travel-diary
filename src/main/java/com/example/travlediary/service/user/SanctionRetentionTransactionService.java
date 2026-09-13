package com.example.travlediary.service.user;

import com.example.travlediary.repository.user.BlockedEmailMapper;
import com.example.travlediary.repository.user.UserSanctionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 보관기간이 끝난 제재 기록 한 대상의 삭제를 한 트랜잭션에서 끝낸다.
 *
 * <p>목록을 읽은 시점과 지우는 시점 사이에 관리자가 제재를 다시 걸거나 회원이 복구했을 수 있다.
 * 그래서 두 DELETE 문 모두 WHERE 에 보관조건을 그대로 다시 담고 있고,
 * 조건이 깨졌으면 한 행도 지우지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SanctionRetentionTransactionService {

    private final UserSanctionMapper userSanctionMapper;
    private final BlockedEmailMapper blockedEmailMapper;

    /**
     * 종료 후 보관기간이 끝난 제재 한 건. 적용중인 제재는 SQL 조건에서 걸러진다.
     *
     * @return 지웠으면 true, 더 이상 대상이 아니면 false
     */
    @Transactional
    public boolean purgeSanction(Long sanctionId, LocalDateTime retentionCutoff) {
        if (sanctionId == null || retentionCutoff == null) {
            return false;
        }
        return userSanctionMapper.deleteRetentionExpiredSanction(sanctionId, retentionCutoff) == 1;
    }

    /**
     * 최종 탈퇴 후 보관기간이 끝난 회원의 재가입 차단 기록 전체.
     *
     * @return 지운 행 수. 더 이상 대상이 아니면 0
     */
    @Transactional
    public int purgeReSignupBlocksOf(Long userId, LocalDateTime retentionCutoff) {
        if (userId == null || retentionCutoff == null) {
            return 0;
        }
        return blockedEmailMapper.deleteReSignupBlocksOfRetentionExpiredUser(
                userId, retentionCutoff);
    }
}
