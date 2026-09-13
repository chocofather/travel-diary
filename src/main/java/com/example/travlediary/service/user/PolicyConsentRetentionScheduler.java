package com.example.travlediary.service.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 최종 탈퇴 후 3년이 지난 약관/개인정보 동의 이력을 정리하는 배치.
 *
 * <p>회원 파기 배치(AccountPurgeScheduler)와는 분리된 별도 수명이다.
 * 파기 배치는 유예가 끝난 회원을 tombstone 으로 만들고, 이 배치는 그 뒤 3년이 지난
 * 동의 이력만 정리한다. users tombstone 행 자체는 그대로 남는다.
 */
@Component
public class PolicyConsentRetentionScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(PolicyConsentRetentionScheduler.class);

    private final PolicyConsentRetentionBatchService policyConsentRetentionBatchService;
    private final Clock clock;

    @Autowired
    public PolicyConsentRetentionScheduler(
            PolicyConsentRetentionBatchService policyConsentRetentionBatchService) {
        this(policyConsentRetentionBatchService, Clock.systemDefaultZone());
    }

    PolicyConsentRetentionScheduler(
            PolicyConsentRetentionBatchService policyConsentRetentionBatchService,
            Clock clock) {
        this.policyConsentRetentionBatchService = policyConsentRetentionBatchService;
        this.clock = clock;
    }

    /**
     * 매일 새벽 03:30. 보관기간이 3년 단위라 하루 한 번이면 충분하다.
     * 매시 도는 기존 배치(정각/:10/:20)와 문의 보존기간 배치(04:40) 어느 쪽과도 겹치지 않는다.
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpiredPolicyConsents() {
        try {
            policyConsentRetentionBatchService.purgeExpiredConsents(LocalDateTime.now(clock));
        } catch (RuntimeException exception) {
            log.error("Failed to run the policy consent retention batch", exception);
        }
    }
}
