package com.example.travlediary.service.user;

import java.time.LocalDateTime;
import java.time.Period;

/**
 * 제재 이력과 재가입 방지 기록의 보관기간 정책.
 *
 * <p>두 기록은 목적이 달라 기준일도 다르다. 혼동하면 보관기간이 부당하게 늘거나 줄어든다.
 *
 * <ul>
 *   <li>제재 증빙 이력(user_sanctions) : 기준일은 <strong>제재 종료 시각(released_at)</strong>.
 *       계정이 살아 있는 동안 적용중인 제재는 기간과 무관하게 유지한다. 회원이 나중에 탈퇴하더라도
 *       이미 종료된 제재의 보관기간을 탈퇴일부터 다시 세지 않는다.
 *       예외가 하나 있다. 적용중인 영구제재는 released_at 이 영원히 NULL 이라 이 기준으로는
 *       절대 정리되지 않으므로, 그 회원이 최종 탈퇴한 경우에 한해
 *       <strong>최종 탈퇴 완료 시각(users.deleted_at)</strong> 기준으로 센다.</li>
 *   <li>재가입 방지 기록(blocked_emails) : 기준일은 <strong>최종 탈퇴 완료 시각(users.deleted_at)</strong>.
 *       "탈퇴 후 재가입 차단"이 목적이라 수명이 탈퇴 시점부터 시작한다.</li>
 * </ul>
 *
 * <p>차단 기록은 원본 이메일이 아니라 {@link EmailHasher} 의 HMAC 결과만 담고 있어,
 * 보관 중에도 이메일 원문이 남지 않는다.
 */
public final class SanctionRetentionPolicy {

    /** 종료/최종 탈퇴 후 3년. 두 기록 모두 기간 자체는 같다. */
    public static final Period RETENTION = Period.ofYears(3);

    private SanctionRetentionPolicy() {
    }

    /**
     * 이 시각 이전에 종료(또는 최종 탈퇴)된 기록이 정리 대상이다.
     * 경계는 "이하"라서 정확히 3년이 되는 시점부터 삭제할 수 있다.
     */
    public static LocalDateTime retentionCutoff(LocalDateTime currentTime) {
        return currentTime.minus(RETENTION);
    }
}
