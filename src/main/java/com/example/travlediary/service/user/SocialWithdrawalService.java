package com.example.travlediary.service.user;

import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SocialWithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(SocialWithdrawalService.class);
    private static final Duration WITHDRAWAL_TTL = Duration.ofMinutes(10);
    private static final String RETRY_MESSAGE =
            "본인 확인 또는 소셜 계정 연결 해제 중 문제가 발생했습니다. 다시 시도해주세요.";

    private final SocialAccountService socialAccountService;
    private final SocialProviderUnlinkClient providerUnlinkClient;
    private final MyPageAccountService accountService;
    private final Clock clock;

    @Autowired
    public SocialWithdrawalService(SocialAccountService socialAccountService,
                                   SocialProviderUnlinkClient providerUnlinkClient,
                                   MyPageAccountService accountService) {
        this(socialAccountService, providerUnlinkClient, accountService, Clock.systemUTC());
    }

    SocialWithdrawalService(SocialAccountService socialAccountService,
                            SocialProviderUnlinkClient providerUnlinkClient,
                            MyPageAccountService accountService,
                            Clock clock) {
        this.socialAccountService = socialAccountService;
        this.providerUnlinkClient = providerUnlinkClient;
        this.accountService = accountService;
        this.clock = clock;
    }

    public PendingSocialWithdrawal begin(Long userId) {
        // begin()의 오류만 계정 관리 화면에 그대로 노출되므로 여기에만 메시지 코드를 얹는다.
        if (userId == null || accountService.hasLocalPassword(userId)) {
            throw new SocialWithdrawalException("mypage.account.error.social.cannotStart",
                    "소셜 계정 탈퇴를 시작할 수 없습니다.");
        }

        List<SocialAccount> accounts = socialAccountService.findAllByUserId(userId);
        if (accounts == null || accounts.isEmpty()) {
            throw unknownAccount();
        }
        if (accounts.size() != 1) {
            throw new SocialWithdrawalException(
                    "mypage.account.error.social.multipleProviders",
                    "여러 로그인 수단이 연결된 계정은 현재 탈퇴를 처리할 수 없습니다.");
        }

        SocialAccount account = accounts.get(0);
        if (account == null || account.getProvider() == null
                || account.getUserId() == null || !userId.equals(account.getUserId())
                || account.getProviderUserId() == null
                || account.getProviderUserId().isBlank()) {
            throw unknownAccount();
        }

        Instant createdAt = clock.instant();
        return new PendingSocialWithdrawal(
                UUID.randomUUID().toString(),
                userId,
                account.getProvider(),
                createdAt,
                createdAt.plus(WITHDRAWAL_TTL));
    }

    public void complete(PendingSocialWithdrawal pending,
                         Long currentUserId,
                         SocialProvider authenticatedProvider,
                         String authenticatedProviderUserId,
                         String accessToken) {
        validateIntent(pending, currentUserId, authenticatedProvider);
        String providerUserId = normalize(authenticatedProviderUserId);
        String token = normalize(accessToken);
        if (providerUserId == null || token == null) {
            throw new SocialWithdrawalException(RETRY_MESSAGE);
        }

        List<SocialAccount> accounts = socialAccountService.findAllByUserId(currentUserId);
        if (accounts == null || accounts.size() != 1) {
            throw new SocialWithdrawalException(accounts != null && accounts.size() > 1
                    ? "여러 로그인 수단이 연결된 계정은 현재 탈퇴를 처리할 수 없습니다."
                    : RETRY_MESSAGE);
        }

        SocialAccount account = socialAccountService.findByUserIdAndProvider(
                currentUserId, authenticatedProvider);
        if (account == null
                || account.getUserId() == null
                || !currentUserId.equals(account.getUserId())
                || account.getProvider() != authenticatedProvider
                || !providerUserId.equals(account.getProviderUserId())) {
            throw new SocialWithdrawalException(RETRY_MESSAGE);
        }

        // 30일 유예 동안에는 provider 연결을 끊지 않는다.
        // 최종 파기 단계에서 providerUnlinkClient.unlink() 를 호출한다.
        try {
            accountService.withdrawAfterSocialReauthentication(currentUserId);
        } catch (RuntimeException exception) {
            log.error("Social withdrawal request failed. userId={}, provider={}",
                    currentUserId, authenticatedProvider, exception);
            throw new SocialWithdrawalException(
                    "회원 탈퇴 처리 중 문제가 발생했습니다. 고객센터에 문의해주세요.");
        }
    }

    public boolean isValid(PendingSocialWithdrawal pending, Long currentUserId) {
        return pending != null
                && pending.isValidAt(clock.instant())
                && currentUserId != null
                && currentUserId.equals(pending.userId());
    }

    private void validateIntent(PendingSocialWithdrawal pending,
                                Long currentUserId,
                                SocialProvider authenticatedProvider) {
        if (!isValid(pending, currentUserId)
                || authenticatedProvider == null
                || authenticatedProvider != pending.provider()) {
            throw new SocialWithdrawalException(RETRY_MESSAGE);
        }
    }

    private SocialWithdrawalException unknownAccount() {
        return new SocialWithdrawalException("mypage.account.error.social.accountUnknown",
                "로그인 계정 정보를 확인할 수 없습니다.");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
