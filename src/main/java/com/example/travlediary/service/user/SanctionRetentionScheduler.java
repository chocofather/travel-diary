package com.example.travlediary.service.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 보관기간이 끝난 제재 이력과 재가입 방지 기록을 정리하는 배치.
 *
 * <p>{@link SanctionExpiryScheduler}와는 역할이 다르다. 그쪽은 기간이 지난 제재를
 * 매시 만료시켜 회원을 풀어주는 일이고, 이쪽은 이미 종료된 기록의 보관기간이
 * 끝났을 때 지우는 일이다. 두 책임을 섞지 않는다.
 */
@Component
public class SanctionRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SanctionRetentionScheduler.class);

    private final SanctionRetentionBatchService sanctionRetentionBatchService;
    private final Clock clock;

    @Autowired
    public SanctionRetentionScheduler(SanctionRetentionBatchService sanctionRetentionBatchService) {
        this(sanctionRetentionBatchService, Clock.systemDefaultZone());
    }

    SanctionRetentionScheduler(SanctionRetentionBatchService sanctionRetentionBatchService,
                               Clock clock) {
        this.sanctionRetentionBatchService = sanctionRetentionBatchService;
        this.clock = clock;
    }

    /**
     * 매일 새벽 03:50. 보관기간이 3년 단위라 하루 한 번이면 충분하다.
     * 매시 도는 배치(정각/:10/:20), 동의 이력 보관(03:30), 문의 보존기간(04:40)
     * 어느 쪽과도 겹치지 않는다.
     */
    @Scheduled(cron = "0 50 3 * * *")
    public void purgeExpiredSanctionRecords() {
        try {
            sanctionRetentionBatchService.purgeExpiredSanctionRecords(LocalDateTime.now(clock));
        } catch (RuntimeException exception) {
            log.error("Failed to run the sanction retention batch", exception);
        }
    }
}
