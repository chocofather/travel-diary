package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 탈퇴 유예(WITHDRAWAL_PENDING) 계정의 복구 링크에 쓰는 일회성 토큰.
 * 원문 토큰은 메일 URL 에만 존재하고 DB 에는 SHA-256 해시만 남으므로
 * 이 모델도 원문 토큰을 담지 않는다.
 */
@Data
@NoArgsConstructor
public class AccountRecoveryToken {

    private Long id;
    private Long userId;
    /** 링크 자체의 만료 시각. users.purge_scheduled_at 과는 별개다. */
    private LocalDateTime expiresAt;
    /** 사용 완료 또는 재발급으로 닫힌 시각. 아직 살아 있으면 null 이다. */
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
