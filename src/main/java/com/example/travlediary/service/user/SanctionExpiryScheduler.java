package com.example.travlediary.service.user;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 기간제한 자동 만료 배치. 로그인 시 보조 확인과 함께 2중으로 동작한다. */
@Component
@RequiredArgsConstructor
public class SanctionExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SanctionExpiryScheduler.class);

    private final UserSanctionService userSanctionService;

    /** 매시 정각에 만료된 기간제한을 해제한다. */
    @Scheduled(cron = "0 0 * * * *")
    public void expireDueSanctions() {
        try {
            userSanctionService.expireDueSanctions(LocalDateTime.now());
        } catch (RuntimeException exception) {
            log.error("Failed to expire due sanctions", exception);
        }
    }
}