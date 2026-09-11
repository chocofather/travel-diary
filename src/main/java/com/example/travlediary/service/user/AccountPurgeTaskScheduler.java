package com.example.travlediary.service.user;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 최종 파기의 파일 삭제 후처리 배치. */
@Component
@RequiredArgsConstructor
public class AccountPurgeTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeTaskScheduler.class);

    private final AccountPurgeFileTaskWorker accountPurgeFileTaskWorker;
    private final AccountPurgeSocialUnlinkWorker accountPurgeSocialUnlinkWorker;

    /**
     * 매시 10분. 매시 정각의 계정 파기 배치가 task 를 만드는 동안과 겹치지 않게 비켜 둔다.
     * 스케줄러 자체는 트랜잭션을 열지 않고, task 별 경계는 worker 아래에서 잡는다.
     */
    @Scheduled(cron = "0 10 * * * *")
    public void processFileDeleteTasks() {
        try {
            accountPurgeFileTaskWorker.processReadyFileDeleteTasks(LocalDateTime.now());
        } catch (RuntimeException exception) {
            log.error("Failed to run the purge file task batch", exception);
        }
    }

    /** 매시 20분. 파일 삭제 배치와도 겹치지 않게 둔다. 카카오 연결 해제만 처리한다. */
    @Scheduled(cron = "0 20 * * * *")
    public void processSocialUnlinkTasks() {
        try {
            accountPurgeSocialUnlinkWorker.processReadyUnlinkTasks(LocalDateTime.now());
        } catch (RuntimeException exception) {
            log.error("Failed to run the purge social unlink batch", exception);
        }
    }
}
