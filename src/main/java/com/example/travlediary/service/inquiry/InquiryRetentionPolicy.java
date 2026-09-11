package com.example.travlediary.service.inquiry;

import java.time.LocalDateTime;
import java.time.Period;

/**
 * 1:1 문의와 답변의 보존기간 정책.
 *
 * <p>회원 탈퇴와는 별개의 수명이다. 탈퇴해도 문의/답변 업무기록은 남고, 탈퇴하지 않았더라도
 * 처리가 끝나고 보존기간이 지나면 똑같이 정리된다. 영구 보존하지 않는다.
 *
 * <p>보존기간의 기준 시점은 "문의 처리가 끝난 시각"이다. 문의 접수 시각이 아니다.
 */
public final class InquiryRetentionPolicy {

    /** 소비자 불만·분쟁 처리 기록 보존기간과 같은 3년. */
    public static final Period RETENTION = Period.ofYears(3);

    private InquiryRetentionPolicy() {
    }

    /**
     * 이 시각 이전에 처리가 끝난 문의가 정리 대상이다.
     * 경계는 "이하"라서 정확히 3년이 지난 문의도 대상이 된다.
     */
    public static LocalDateTime retentionCutoff(LocalDateTime currentTime) {
        return currentTime.minus(RETENTION);
    }
}
