package com.example.travlediary.service.inquiry;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 문의 보존기간 경계.
 *
 * <p>기준 시각은 문의가 접수된 때가 아니라 처리가 끝난 때다.
 * 실제 선별은 SQL 이 {@code 처리종료시각 &lt;= retentionCutoff} 로 하고
 * (InquiryRetentionMapperContractTest 가 그 비교를 못 박는다),
 * 여기서는 그 cutoff 가 언제인지와 경계 포함 여부를 고정한다.
 */
class InquiryRetentionPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 4, 40);

    @Test
    void theRetentionPeriodIsThreeYears() {
        assertThat(InquiryRetentionPolicy.RETENTION.getYears()).isEqualTo(3);
        assertThat(InquiryRetentionPolicy.retentionCutoff(NOW))
                .isEqualTo(LocalDateTime.of(2023, 9, 12, 4, 40));
    }

    /** 처리가 끝난 지 3년이 안 됐으면 남긴다. */
    @Test
    void aFinishedInquiryInsideTheRetentionPeriodIsKept() {
        assertThat(expired(NOW.minusYears(3).plusSeconds(1))).isFalse();
        assertThat(expired(NOW.minusYears(2))).isFalse();
        assertThat(expired(NOW)).isFalse();
    }

    /** 정확히 3년이 지난 시점부터 정리 대상이다. */
    @Test
    void theExactThreeYearMarkIsAlreadyExpired() {
        assertThat(expired(NOW.minusYears(3))).isTrue();
    }

    @Test
    void anythingOlderThanThreeYearsIsExpired() {
        assertThat(expired(NOW.minusYears(3).minusSeconds(1))).isTrue();
        assertThat(expired(NOW.minusYears(10))).isTrue();
    }

    /** SQL 의 비교와 같은 경계(이하)를 쓴다. */
    private boolean expired(LocalDateTime processedAt) {
        return !processedAt.isAfter(InquiryRetentionPolicy.retentionCutoff(NOW));
    }
}
