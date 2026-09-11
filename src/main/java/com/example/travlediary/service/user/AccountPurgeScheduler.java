package com.example.travlediary.service.user;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 30일 탈퇴 유예가 끝난 회원의 최종 파기 배치. 기간제한 만료 배치와 같은 패턴을 쓴다. */
@Component
@RequiredArgsConstructor
public class AccountPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeScheduler.class);

    private final AccountPurgeBatchService accountPurgeBatchService;

    /**
     * 매시 정각. 유예가 30일 단위라 분 단위 정밀도는 필요 없다.
     * 스케줄러 자체는 트랜잭션을 열지 않고, 회원별 경계는 배치 서비스 아래에서 잡는다.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void purgeDueAccounts() {
        try {
            accountPurgeBatchService.purgeDueAccounts(LocalDateTime.now());
        } catch (RuntimeException exception) {
            log.error("Failed to run the account purge batch", exception);
        }
    }
}
