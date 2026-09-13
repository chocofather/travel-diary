package com.example.travlediary.service.user;

import java.time.LocalDateTime;
import java.time.Period;

/**
 * 약관/개인정보 동의 이력(user_policy_consents)의 보관기간 정책.
 *
 * <p>동의 이력은 동의 사실을 증빙하는 기록이라 회원이 존재하는 동안에는 전부 보관한다.
 * 30일 탈퇴 유예 중에도 마찬가지다. 최종 탈퇴가 끝난 뒤에만 수명이 시작된다.
 *
 * <p>보관기간의 기준 시점은 <strong>users.deleted_at</strong>, 즉 최종 파기가 끝난 시각이다.
 * withdrawal_requested_at 이나 purge_scheduled_at 이 아니다. 탈퇴 신청 2026-01-01,
 * 최종 파기 2026-01-31 이면 삭제 가능 시점은 2029-01-31 이다.
 */
public final class PolicyConsentRetentionPolicy {

    /** 최종 탈퇴 완료 후 3년. */
    public static final Period RETENTION = Period.ofYears(3);

    private PolicyConsentRetentionPolicy() {
    }

    /**
     * 이 시각 이전에 최종 탈퇴가 끝난 회원의 동의 이력이 정리 대상이다.
     * 경계는 "이하"라서 정확히 3년이 되는 시점부터 삭제할 수 있다.
     */
    public static LocalDateTime retentionCutoff(LocalDateTime currentTime) {
        return currentTime.minus(RETENTION);
    }
}
