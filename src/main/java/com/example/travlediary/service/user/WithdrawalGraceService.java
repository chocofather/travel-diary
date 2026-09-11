package com.example.travlediary.service.user;

import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 탈퇴 유예가 아직 남아 있는지 판정하고, 끝났으면 그 자리에서 최종 파기까지 마친다.
 *
 * <p>유예 여부의 기준은 배치 실행 여부가 아니라 언제나 users.purge_scheduled_at 이다.
 * {@link AccountPurgeScheduler}는 매시 정각에만 돌기 때문에 "유예는 끝났는데 아직 파기되지 않은"
 * 구간이 최대 한 시간 생기는데, 그동안 이 계정을 유예 중인 것처럼 다루면
 * 복구도 못 하고 기존 email/username/nickname/social 연결은 그대로 점유한 채
 * 새로 가입할 수도 없는 상태에 갇힌다. 그래서 요청 시점에 직접 판정한다.
 *
 * <p>여기서 실행하는 파기는 배치의 파기와 같은 절차를 쓴다. 단 방금 본인 인증에 사용한
 * provider 만은 연결 해제 task 를 만들지 않는다({@link AccountPurgeOptions}).
 */
@Service
public class WithdrawalGraceService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalGraceService.class);

    private final UserMapper userMapper;
    private final AccountPurgeTransactionService accountPurgeTransactionService;
    private final Clock clock;

    @Autowired
    public WithdrawalGraceService(UserMapper userMapper,
                                  AccountPurgeTransactionService accountPurgeTransactionService) {
        this(userMapper, accountPurgeTransactionService, Clock.systemDefaultZone());
    }

    WithdrawalGraceService(UserMapper userMapper,
                           AccountPurgeTransactionService accountPurgeTransactionService,
                           Clock clock) {
        this.userMapper = userMapper;
        this.accountPurgeTransactionService = accountPurgeTransactionService;
        this.clock = clock;
    }

    /**
     * 인증이 끝난 회원 한 명의 탈퇴 유예 상태를 판정한다.
     * 유예가 끝났으면 기존 계정을 즉시 최종 파기해 식별정보 점유를 풀어 준다.
     *
     * <p>파기는 되돌릴 수 없으므로 호출하는 쪽에서 반드시 본인 확인(비밀번호 검증이나
     * provider OAuth 인증)을 마친 뒤에 불러야 한다.
     *
     * @param reauthenticatedProvider 방금 그 계정으로 로그인에 성공한 provider.
     *                                일반 로그인이면 null
     */
    public Outcome resolveAccess(Long userId, SocialProvider reauthenticatedProvider) {
        if (userId == null) {
            return Outcome.NOT_PENDING;
        }
        if (userMapper.findStatusById(userId) != UserStatus.WITHDRAWAL_PENDING) {
            return Outcome.NOT_PENDING;
        }

        User account = userMapper.findWithdrawalPendingById(userId);
        if (account == null) {
            return Outcome.NOT_PENDING;
        }
        if (!isGraceEnded(account.getPurgeScheduledAt())) {
            return Outcome.IN_GRACE;
        }

        finalizeExpiredAccount(userId, reauthenticatedProvider);
        return Outcome.GRACE_ENDED;
    }

    /**
     * 유예 종료 경계. purge_scheduled_at 과 정확히 같은 시각이면 이미 끝난 것으로 본다.
     * 일정이 비어 있으면 판단할 근거가 없으므로 끝났다고 보지 않는다.
     */
    public boolean isGraceEnded(LocalDateTime purgeScheduledAt) {
        return purgeScheduledAt != null && !purgeScheduledAt.isAfter(LocalDateTime.now(clock));
    }

    /**
     * 파기에 실패해도 유예가 끝났다는 판정 자체는 바뀌지 않는다. 복구는 어차피 불가능하고,
     * 남은 계정은 다음 배치가 다시 집어간다. 여기서 예외를 그대로 올리면
     * 사용자는 아무것도 할 수 없는 화면에 그대로 갇히므로 기록만 남기고 넘어간다.
     */
    private void finalizeExpiredAccount(Long userId, SocialProvider reauthenticatedProvider) {
        AccountPurgeOptions options = reauthenticatedProvider == null
                ? AccountPurgeOptions.scheduled()
                : AccountPurgeOptions.reauthenticated(reauthenticatedProvider);
        try {
            AccountPurgeTransactionService.PurgeOutcome outcome =
                    accountPurgeTransactionService.purgeOne(userId, LocalDateTime.now(clock), options);
            log.info("Expired withdrawal finalized on access: userId={}, purged={}, purgeJobId={}",
                    userId, outcome.purged(), outcome.purgeJobId());
        } catch (RuntimeException exception) {
            log.error("Expired withdrawal could not be finalized on access,"
                            + " the account stays pending: userId={}, exceptionType={}",
                    userId, exception.getClass().getSimpleName(), exception);
        }
    }

    /** 인증된 요청을 어디로 보낼지 결정하는 세 가지 상태. */
    public enum Outcome {
        /** 탈퇴 유예 회원이 아니다. 평소대로 진행한다. */
        NOT_PENDING,
        /** 아직 유예 중이다. 전용 안내 화면으로 격리하고 복구를 허용한다. */
        IN_GRACE,
        /** 유예가 끝났다. 기존 계정은 최종 파기했고 더는 안내 화면에 들이지 않는다. */
        GRACE_ENDED
    }
}
