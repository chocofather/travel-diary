package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 탈퇴 유예가 끝난 회원 한 명의 최종 파기 작업.
 * account_purge_jobs 는 user_id UNIQUE 라 회원당 한 건만 존재한다.
 */
@Data
@NoArgsConstructor
public class AccountPurgeJob {
    private Long id;
    private Long userId;
    private AccountPurgeJobStatus status;
    /** DB 안의 파기와 회원 익명화가 끝난 시각. */
    private LocalDateTime dbCompletedAt;
    /** 파일/외부 연동 후처리까지 끝난 시각. */
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
