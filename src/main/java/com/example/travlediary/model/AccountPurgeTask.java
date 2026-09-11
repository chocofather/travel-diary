package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 한 파기 작업에 딸린 후처리 한 건.
 *
 * <p>DB row 를 지우기 전에 파일 경로나 provider 식별값을 여기로 옮겨 두어,
 * 서버가 중간에 멈춰도 나중에 다시 시도할 수 있게 한다. 실행 worker 는 아직 없다.
 */
@Data
@NoArgsConstructor
public class AccountPurgeTask {
    private Long id;
    private Long purgeJobId;
    private AccountPurgeTaskType taskType;
    /** 외부 provider 작업일 때만 채운다. 파일 삭제에서는 null. */
    private SocialProvider provider;
    /** FILE_DELETE 면 관리 업로드 경로, SOCIAL_UNLINK 면 provider 사용자 식별값. */
    private String targetValue;
    private AccountPurgeTaskStatus status;
    private int attempts;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime completedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AccountPurgeTask fileDelete(Long purgeJobId, String targetValue) {
        return pending(purgeJobId, AccountPurgeTaskType.FILE_DELETE, null, targetValue);
    }

    public static AccountPurgeTask socialUnlink(Long purgeJobId, SocialProvider provider,
                                                String providerUserId) {
        return pending(purgeJobId, AccountPurgeTaskType.SOCIAL_UNLINK, provider, providerUserId);
    }

    private static AccountPurgeTask pending(Long purgeJobId, AccountPurgeTaskType taskType,
                                            SocialProvider provider, String targetValue) {
        AccountPurgeTask task = new AccountPurgeTask();
        task.setPurgeJobId(purgeJobId);
        task.setTaskType(taskType);
        task.setProvider(provider);
        task.setTargetValue(targetValue);
        task.setStatus(AccountPurgeTaskStatus.PENDING);
        task.setAttempts(0);
        return task;
    }
}
