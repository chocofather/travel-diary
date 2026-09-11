package com.example.travlediary.repository.user;

import com.example.travlediary.model.AccountRecoveryToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AccountRecoveryTokenMapper {

    /** 발급 이력을 남기는 구조라 항상 새 행을 넣는다. token_hash 만 저장하고 원문은 넣지 않는다. */
    int insertToken(@Param("userId") Long userId,
                    @Param("tokenHash") String tokenHash,
                    @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * 새 링크를 발급하기 전에 같은 회원의 살아 있는 링크를 모두 닫는다.
     * 전용 invalidated_at 컬럼이 없으므로 used_at 을 "더 이상 쓸 수 없게 된 시각"으로 함께 쓴다.
     * 행을 지우지 않으므로 발급 이력은 그대로 남는다.
     */
    int invalidateUnusedTokens(@Param("userId") Long userId,
                               @Param("invalidatedAt") LocalDateTime invalidatedAt);

    /** 재발송 쿨다운 판단용. 마지막 발급 시각만 본다. */
    LocalDateTime findLatestIssuedAt(@Param("userId") Long userId);

    /** 아직 쓸 수 있는 토큰만 돌려준다. 사용/무효 처리됐거나 만료됐으면 null 이다. */
    AccountRecoveryToken findUsableByTokenHash(@Param("tokenHash") String tokenHash,
                                               @Param("currentTime") LocalDateTime currentTime);

    /** used_at IS NULL 조건으로 단 한 번만 성공한다. 같은 링크의 재사용은 0 을 돌려준다. */
    int markUsed(@Param("id") Long id,
                 @Param("usedAt") LocalDateTime usedAt);
}
