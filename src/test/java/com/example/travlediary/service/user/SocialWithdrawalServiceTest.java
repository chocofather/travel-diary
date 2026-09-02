package com.example.travlediary.service.user;

import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialWithdrawalServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T03:00:00Z");

    @Mock private SocialAccountService socialAccountService;
    @Mock private SocialProviderUnlinkClient providerUnlinkClient;
    @Mock private MyPageAccountService accountService;

    private SocialWithdrawalService service;

    @BeforeEach
    void setUp() {
        service = new SocialWithdrawalService(
                socialAccountService,
                providerUnlinkClient,
                accountService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void startsOneTimeIntentOnlyForTheCurrentSocialOnlyUserWithExactlyOneProvider() {
        when(accountService.hasLocalPassword(7L)).thenReturn(false);
        when(socialAccountService.findAllByUserId(7L))
                .thenReturn(List.of(account(7L, SocialProvider.NAVER, "naver-id")));

        PendingSocialWithdrawal pending = service.begin(7L);

        assertThat(pending.flowId()).isNotBlank();
        assertThat(pending.userId()).isEqualTo(7L);
        assertThat(pending.provider()).isEqualTo(SocialProvider.NAVER);
        assertThat(pending.createdAt()).isEqualTo(NOW);
        assertThat(Duration.between(pending.createdAt(), pending.expiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void refusesZeroOrMultipleProvidersWithoutChoosingTheFirstOne() {
        when(accountService.hasLocalPassword(7L)).thenReturn(false);
        when(socialAccountService.findAllByUserId(7L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.begin(7L))
                .isInstanceOf(SocialWithdrawalException.class);

        when(socialAccountService.findAllByUserId(7L)).thenReturn(List.of(
                account(7L, SocialProvider.GOOGLE, "google-sub"),
                account(7L, SocialProvider.KAKAO, "kakao-sub")));

        assertThatThrownBy(() -> service.begin(7L))
                .isInstanceOf(SocialWithdrawalException.class)
                .hasMessage("여러 로그인 수단이 연결된 계정은 현재 탈퇴를 처리할 수 없습니다.");
        verify(providerUnlinkClient, never()).unlink(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @ParameterizedTest
    @EnumSource(SocialProvider.class)
    void matchingProviderIdentityIsUnlinkedBeforeExistingWithdrawalPolicyRuns(
            SocialProvider provider) {
        String providerUserId = provider.name().toLowerCase() + "-identity";
        PendingSocialWithdrawal pending = pending(7L, provider);
        when(socialAccountService.findAllByUserId(7L))
                .thenReturn(List.of(account(7L, provider, providerUserId)));
        when(socialAccountService.findByUserIdAndProvider(7L, provider))
                .thenReturn(account(7L, provider, providerUserId));

        service.complete(
                pending, 7L, provider, providerUserId, "one-use-access-token");

        InOrder order = inOrder(providerUnlinkClient, accountService);
        order.verify(providerUnlinkClient).unlink(
                provider, "one-use-access-token", providerUserId);
        order.verify(accountService).withdrawAfterSocialReauthentication(7L);
    }

    @ParameterizedTest
    @EnumSource(SocialProvider.class)
    void differentProviderSubjectNeverUnlinksOrWithdrawsEvenWhenEmailCouldMatch(
            SocialProvider provider) {
        PendingSocialWithdrawal pending = pending(7L, provider);
        when(socialAccountService.findAllByUserId(7L))
                .thenReturn(List.of(account(7L, provider, "provider-account-a")));
        when(socialAccountService.findByUserIdAndProvider(7L, provider))
                .thenReturn(account(7L, provider, "provider-account-a"));

        assertThatThrownBy(() -> service.complete(
                pending, 7L, provider,
                "provider-account-b", "one-use-access-token"))
                .isInstanceOf(SocialWithdrawalException.class);

        verify(providerUnlinkClient, never()).unlink(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(accountService, never()).withdrawAfterSocialReauthentication(7L);
    }

    @Test
    void expiredOrCrossUserIntentCannotReachProvider() {
        PendingSocialWithdrawal expired = new PendingSocialWithdrawal(
                "flow", 7L, SocialProvider.KAKAO,
                NOW.minusSeconds(700), NOW.minusSeconds(100));

        assertThatThrownBy(() -> service.complete(
                expired, 7L, SocialProvider.KAKAO, "kakao-sub", "token"))
                .isInstanceOf(SocialWithdrawalException.class);
        assertThatThrownBy(() -> service.complete(
                pending(7L, SocialProvider.KAKAO), 99L,
                SocialProvider.KAKAO, "kakao-sub", "token"))
                .isInstanceOf(SocialWithdrawalException.class);

        verify(providerUnlinkClient, never()).unlink(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @ParameterizedTest
    @EnumSource(SocialProvider.class)
    void providerFailureStopsBeforeTravelDiaryWithdrawal(SocialProvider provider) {
        String providerUserId = provider.name().toLowerCase() + "-identity";
        PendingSocialWithdrawal pending = pending(7L, provider);
        when(socialAccountService.findAllByUserId(7L))
                .thenReturn(List.of(account(7L, provider, providerUserId)));
        when(socialAccountService.findByUserIdAndProvider(7L, provider))
                .thenReturn(account(7L, provider, providerUserId));
        org.mockito.Mockito.doThrow(new SocialProviderUnlinkException())
                .when(providerUnlinkClient)
                .unlink(provider, "token", providerUserId);

        assertThatThrownBy(() -> service.complete(
                pending, 7L, provider, providerUserId, "token"))
                .isInstanceOf(SocialWithdrawalException.class);

        verify(accountService, never()).withdrawAfterSocialReauthentication(7L);
    }

    @Test
    void aSecondProviderAddedBeforeCallbackBlocksPartialWithdrawal() {
        PendingSocialWithdrawal pending = pending(7L, SocialProvider.GOOGLE);
        when(socialAccountService.findAllByUserId(7L)).thenReturn(List.of(
                account(7L, SocialProvider.GOOGLE, "google-sub"),
                account(7L, SocialProvider.NAVER, "naver-id")));

        assertThatThrownBy(() -> service.complete(
                pending, 7L, SocialProvider.GOOGLE, "google-sub", "token"))
                .isInstanceOf(SocialWithdrawalException.class)
                .hasMessage("여러 로그인 수단이 연결된 계정은 현재 탈퇴를 처리할 수 없습니다.");

        verify(providerUnlinkClient, never()).unlink(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(accountService, never()).withdrawAfterSocialReauthentication(7L);
    }

    @Test
    void databaseFailureAfterProviderSuccessIsReportedAsFailureWithoutRetryingUnlink() {
        PendingSocialWithdrawal pending = pending(7L, SocialProvider.NAVER);
        when(socialAccountService.findAllByUserId(7L))
                .thenReturn(List.of(account(7L, SocialProvider.NAVER, "naver-id")));
        when(socialAccountService.findByUserIdAndProvider(7L, SocialProvider.NAVER))
                .thenReturn(account(7L, SocialProvider.NAVER, "naver-id"));
        org.mockito.Mockito.doThrow(new IllegalStateException("database failure"))
                .when(accountService).withdrawAfterSocialReauthentication(7L);

        assertThatThrownBy(() -> service.complete(
                pending, 7L, SocialProvider.NAVER, "naver-id", "token"))
                .isInstanceOf(SocialWithdrawalException.class)
                .hasMessage("회원 탈퇴 처리 중 문제가 발생했습니다. 고객센터에 문의해주세요.");

        verify(providerUnlinkClient).unlink(SocialProvider.NAVER, "token", "naver-id");
        verify(accountService).withdrawAfterSocialReauthentication(7L);
    }

    @Test
    void externalUnlinkCoordinatorIsNotWrappedInADatabaseTransaction()
            throws NoSuchMethodException {
        Transactional transactional = SocialWithdrawalService.class
                .getMethod("complete", PendingSocialWithdrawal.class,
                        Long.class, SocialProvider.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNull();
    }

    private PendingSocialWithdrawal pending(Long userId, SocialProvider provider) {
        return new PendingSocialWithdrawal(
                "flow-id", userId, provider, NOW, NOW.plus(Duration.ofMinutes(10)));
    }

    private SocialAccount account(Long userId, SocialProvider provider, String providerUserId) {
        SocialAccount account = new SocialAccount();
        account.setUserId(userId);
        account.setProvider(provider);
        account.setProviderUserId(providerUserId);
        return account;
    }
}
