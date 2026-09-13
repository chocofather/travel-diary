package com.example.travlediary.service.user;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보관기간 경계. 제재 이력은 제재 종료 시각, 재가입 차단 기록은 최종 탈퇴 시각이 기준이다.
 * 기간 자체는 둘 다 3년으로 같다.
 */
class SanctionRetentionPolicyTest {

    /** 종료 후 3년에서 1분 모자라면 유지, 정각부터 삭제 가능이다. */
    @Test
    void theBoundaryIsInclusiveAtExactlyThreeYears() {
        LocalDateTime releasedAt = LocalDateTime.of(2026, 9, 13, 10, 0);

        assertThat(SanctionRetentionPolicy.retentionCutoff(
                LocalDateTime.of(2029, 9, 13, 9, 59)))
                .as("3년에서 1분 모자라면 유지")
                .isBefore(releasedAt);
        assertThat(SanctionRetentionPolicy.retentionCutoff(
                LocalDateTime.of(2029, 9, 13, 10, 0)))
                .as("정확히 3년이면 삭제 가능")
                .isEqualTo(releasedAt);
        assertThat(SanctionRetentionPolicy.retentionCutoff(
                LocalDateTime.of(2029, 9, 13, 10, 1)))
                .as("3년이 지나면 삭제 가능")
                .isAfter(releasedAt);
    }

    /**
     * 이미 종료된 제재가 있는 회원이 나중에 탈퇴해도, 제재 이력의 보관기간은
     * 탈퇴일부터 다시 시작하지 않는다. 두 기록의 기준일이 다르다는 뜻이다.
     */
    @Test
    void aLaterWithdrawalDoesNotExtendTheSanctionHistoryRetention() {
        LocalDateTime sanctionReleasedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime withdrawalPurgedAt = LocalDateTime.of(2027, 6, 1, 0, 0);

        LocalDateTime cutoff = SanctionRetentionPolicy.retentionCutoff(
                LocalDateTime.of(2029, 1, 1, 0, 0));

        assertThat(cutoff)
                .as("제재 이력은 종료 3년 뒤인 2029-01-01 에 삭제 가능해진다")
                .isEqualTo(sanctionReleasedAt);
        assertThat(cutoff)
                .as("같은 시점에 재가입 차단 기록은 아직 보관기간이 남아 있다")
                .isBefore(withdrawalPurgedAt);
    }

    /** 두 기록의 기간은 같은 3년이고, 같은 계산식을 쓴다. */
    @Test
    void bothRecordsUseTheSameThreeYearPeriod() {
        assertThat(SanctionRetentionPolicy.RETENTION.getYears()).isEqualTo(3);
        assertThat(SanctionRetentionPolicy.RETENTION)
                .isEqualTo(PolicyConsentRetentionPolicy.RETENTION);
    }
}
