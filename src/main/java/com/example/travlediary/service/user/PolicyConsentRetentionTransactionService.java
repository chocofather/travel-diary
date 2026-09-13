package com.example.travlediary.service.user;

import com.example.travlediary.repository.user.UserPolicyConsentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 보관기간이 끝난 회원 한 명의 동의 이력 삭제를 한 트랜잭션에서 끝낸다.
 *
 * <p>회원 단위로 전체를 지운다. 한 회원의 이력 일부만 남는 상태를 만들지 않는다.
 * DELETE 문의 EXISTS 가 보관기간 조건을 다시 확인하므로, 목록을 읽은 뒤 조건이 깨졌으면
 * 한 행도 지우지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PolicyConsentRetentionTransactionService {

    private final UserPolicyConsentMapper userPolicyConsentMapper;

    /**
     * @param retentionCutoff 이 시각 이전에 최종 탈퇴가 끝난 회원만 지운다
     * @return 지운 동의 이력 행 수. 더 이상 대상이 아니면 0
     */
    @Transactional
    public int purgeConsentsOf(Long userId, LocalDateTime retentionCutoff) {
        if (userId == null || retentionCutoff == null) {
            return 0;
        }
        return userPolicyConsentMapper.deleteConsentsOfRetentionExpiredUser(
                userId, retentionCutoff);
    }
}
