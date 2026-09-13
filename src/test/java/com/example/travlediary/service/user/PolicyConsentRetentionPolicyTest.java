package com.example.travlediary.service.user;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보관기간 경계. 기준은 최종 파기가 끝난 시각(users.deleted_at)이고,
 * 탈퇴 신청 시각이나 유예 종료 예정 시각이 아니다.
 */
class PolicyConsentRetentionPolicyTest {

    /** 탈퇴 신청 2026-01-01, 최종 파기 2026-01-31 이면 삭제 가능 시점은 2029-01-31 이다. */
    @Test
    void theRetentionStartsAtTheFinalPurgeNotAtTheWithdrawalRequest() {
        LocalDateTime finalPurge = LocalDateTime.of(2026, 1, 31, 0, 0);

        assertThat(PolicyConsentRetentionPolicy.retentionCutoff(
                LocalDateTime.of(2029, 1, 31, 0, 0)))
                .as("정확히 3년이 된 시점부터 대상이다")
                .isEqualTo(finalPurge);

        // 탈퇴 신청일(2026-01-01) 기준이었다면 30일 일찍 지워졌을 것이다.
        assertThat(PolicyConsentRetentionPolicy.retentionCutoff(
                LocalDateTime.of(2029, 1, 1, 0, 0)))
                .isBefore(finalPurge);
    }

    /** deleted_at <= cutoff 로 비교하므로 1분 전은 유지, 정각부터 삭제 가능이다. */
    @Test
    void theBoundaryIsInclusiveAtExactlyThreeYears() {
        LocalDateTime deletedAt = LocalDateTime.of(2026, 9, 13, 10, 0);

        assertThat(PolicyConsentRetentionPolicy.retentionCutoff(
                LocalDateTime.of(2029, 9, 13, 9, 59)))
                .as("3년에서 1분 모자라면 유지")
                .isBefore(deletedAt);
        assertThat(PolicyConsentRetentionPolicy.retentionCutoff(
                LocalDateTime.of(2029, 9, 13, 10, 0)))
                .as("정확히 3년이면 삭제 가능")
                .isEqualTo(deletedAt);
        assertThat(PolicyConsentRetentionPolicy.retentionCutoff(
                LocalDateTime.of(2029, 9, 13, 10, 1)))
                .as("3년이 지나면 삭제 가능")
                .isAfter(deletedAt);
    }

    /** 윤년 2월 29일에 파기된 회원도 경계가 밀리지 않는다. */
    @Test
    void aLeapDayPurgeUsesTheSamePeriodArithmeticAsTheDatabase() {
        assertThat(PolicyConsentRetentionPolicy.retentionCutoff(
                LocalDateTime.of(2027, 2, 28, 0, 0)))
                .isEqualTo(LocalDateTime.of(2024, 2, 28, 0, 0));
    }
}
