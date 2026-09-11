package com.example.travlediary.service.inquiry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 보존기간(3년)이 끝난 문의를 정리하는 배치.
 *
 * <p>회원 탈퇴 배치와는 분리된 별도 수명이다. 회원의 가입 여부와 무관하게
 * "처리가 끝난 지 3년 지난 문의"를 같은 규칙으로 정리한다.
 */
@Component
public class InquiryRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(InquiryRetentionScheduler.class);

    private final InquiryRetentionBatchService inquiryRetentionBatchService;
    private final Clock clock;

    @Autowired
    public InquiryRetentionScheduler(InquiryRetentionBatchService inquiryRetentionBatchService) {
        this(inquiryRetentionBatchService, Clock.systemDefaultZone());
    }

    InquiryRetentionScheduler(InquiryRetentionBatchService inquiryRetentionBatchService,
                              Clock clock) {
        this.inquiryRetentionBatchService = inquiryRetentionBatchService;
        this.clock = clock;
    }

    /**
     * 매일 새벽 04:40. 보존기간이 3년 단위라 하루 한 번이면 충분하고,
     * 매시 도는 기존 배치(정각/:10/:20)와 시간이 겹치지 않게 둔다.
     */
    @Scheduled(cron = "0 40 4 * * *")
    public void purgeExpiredInquiries() {
        try {
            inquiryRetentionBatchService.purgeExpiredInquiries(LocalDateTime.now(clock));
        } catch (RuntimeException exception) {
            log.error("Failed to run the inquiry retention batch", exception);
        }
    }
}
