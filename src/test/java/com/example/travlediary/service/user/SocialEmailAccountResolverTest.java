package com.example.travlediary.service.user;

import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.user.SocialEmailAccountResolver.EnteredEmailStatus;
import com.example.travlediary.service.user.SocialEmailAccountResolver.Resolution;
import com.example.travlediary.service.user.SocialEmailAccountResolver.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialEmailAccountResolverTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private WithdrawalGraceService withdrawalGraceService;

    private SocialEmailAccountResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SocialEmailAccountResolver(userMapper, withdrawalGraceService);
    }

    /** 이번 단계의 정책은 Google 에만 적용한다. */
    @ParameterizedTest
    @EnumSource(value = SocialProvider.class, names = {"KAKAO", "NAVER"})
    void otherProvidersKeepTheExistingSignupFlowWithoutAnyEmailLookup(SocialProvider provider) {
        Resolution resolution = resolver.resolve(provider, "member@example.com", true);

        assertThat(resolution.type()).isEqualTo(Type.NOT_APPLICABLE);
        assertThat(resolution.email()).isNull();
        verify(userMapper, never()).findByEmail(anyString());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false})
    void googleWithoutAVerifiedEmailIsNeverTrusted(Boolean verified) {
        Resolution resolution = resolver.resolve(
                SocialProvider.GOOGLE, "member@example.com", verified);

        assertThat(resolution.type()).isEqualTo(Type.UNVERIFIED_EMAIL);
        verify(userMapper, never()).findByEmail(anyString());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "not-an-email", "member@"})
    void googleWithoutAUsableEmailAddressIsNeverTrusted(String email) {
        Resolution resolution = resolver.resolve(SocialProvider.GOOGLE, email, true);

        assertThat(resolution.type()).isEqualTo(Type.UNVERIFIED_EMAIL);
        verify(userMapper, never()).findByEmail(anyString());
    }

    @Test
    void anUnusedVerifiedEmailBecomesTheEmailOfANewAccount() {
        Resolution resolution = resolver.resolve(
                SocialProvider.GOOGLE, " Member@Example.COM ", true);

        assertThat(resolution.type()).isEqualTo(Type.NEW_ACCOUNT);
        assertThat(resolution.email()).isEqualTo("member@example.com");
        assertThat(resolution.existingUserId()).isNull();
        verify(userMapper).findByEmail("member@example.com");
    }

    /** 로그인이 허용되는 상태의 계정만 연결 확인 대상이다. */
    @ParameterizedTest
    @CsvSource({"ACTIVE", "RESTRICTED"})
    void anExistingUsableAccountBecomesALinkCandidate(UserStatus status) {
        when(userMapper.findByEmail("member@example.com")).thenReturn(user(17L, status));

        Resolution resolution = resolver.resolve(
                SocialProvider.GOOGLE, "member@example.com", true);

        assertThat(resolution.type()).isEqualTo(Type.LINK_EXISTING);
        assertThat(resolution.existingUserId()).isEqualTo(17L);
        assertThat(resolution.email()).isEqualTo("member@example.com");
    }

    /** 인증 대기 계정은 소셜 로그인으로 가져가지 않고 기존 가입을 마치게 한다. */
    @Test
    void anAccountStillWaitingForEmailVerificationIsNeverTakenOver() {
        when(userMapper.findByEmail("member@example.com"))
                .thenReturn(user(17L, UserStatus.INACTIVE));

        Resolution resolution = resolver.resolve(
                SocialProvider.GOOGLE, "member@example.com", true);

        assertThat(resolution.type()).isEqualTo(Type.VERIFICATION_PENDING);
        assertThat(resolution.existingUserId()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "DEACTIVATED"})
    void dormantAndPurgedAccountsBlockTheFlowEntirely(UserStatus status) {
        when(userMapper.findByEmail("member@example.com")).thenReturn(user(17L, status));

        Resolution resolution = resolver.resolve(
                SocialProvider.GOOGLE, "member@example.com", true);

        assertThat(resolution.type()).isEqualTo(Type.BLOCKED);
        assertThat(resolution.existingUserId()).isNull();
    }

    /** 유예 30일 동안은 그 계정이 이메일을 계속 점유한다. */
    @Test
    void anAccountStillInWithdrawalGraceKeepsHoldingItsEmail() {
        when(userMapper.findByEmail("member@example.com"))
                .thenReturn(user(17L, UserStatus.WITHDRAWAL_PENDING));
        when(withdrawalGraceService.resolveAccess(17L, null))
                .thenReturn(WithdrawalGraceService.Outcome.IN_GRACE);

        Resolution resolution = resolver.resolve(
                SocialProvider.GOOGLE, "member@example.com", true);

        assertThat(resolution.type()).isEqualTo(Type.WITHDRAWAL_PENDING);
        assertThat(resolution.existingUserId()).isNull();
    }

    /** 유예 경계 판정과 만료 파기는 기존 서비스가 맡고, 파기 뒤 이메일 점유를 다시 본다. */
    @Test
    void anExpiredWithdrawalIsFinalizedByTheExistingServiceAndThenFreesTheEmail() {
        when(userMapper.findByEmail("member@example.com"))
                .thenReturn(user(17L, UserStatus.WITHDRAWAL_PENDING))
                .thenReturn(null);
        when(withdrawalGraceService.resolveAccess(17L, null))
                .thenReturn(WithdrawalGraceService.Outcome.GRACE_ENDED);

        Resolution resolution = resolver.resolve(
                SocialProvider.GOOGLE, "member@example.com", true);

        assertThat(resolution.type()).isEqualTo(Type.NEW_ACCOUNT);
        assertThat(resolution.email()).isEqualTo("member@example.com");
        verify(withdrawalGraceService).resolveAccess(17L, null);
    }

    /** 파기가 실패해 행이 남아 있어도 재귀하지 않고 복구 안내로 끝낸다. */
    @Test
    void aFailedFinalizationStopsAtTheWithdrawalNoticeWithoutRetrying() {
        when(userMapper.findByEmail("member@example.com"))
                .thenReturn(user(17L, UserStatus.WITHDRAWAL_PENDING));
        when(withdrawalGraceService.resolveAccess(17L, null))
                .thenReturn(WithdrawalGraceService.Outcome.GRACE_ENDED);

        Resolution resolution = resolver.resolve(
                SocialProvider.GOOGLE, "member@example.com", true);

        assertThat(resolution.type()).isEqualTo(Type.WITHDRAWAL_PENDING);
        // 재판정에서는 파기를 다시 시도하지 않는다.
        verify(withdrawalGraceService, times(1)).resolveAccess(17L, null);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "not-an-email", "member@", "@example.com"})
    void anEnteredEmailThatFailsOurPolicyIsRejectedBeforeAnyLookup(String email) {
        SocialEmailAccountResolver.EnteredEmail entered = resolver.classifyEnteredEmail(email);

        assertThat(entered.status()).isEqualTo(EnteredEmailStatus.INVALID);
        assertThat(entered.email()).isNull();
        verify(userMapper, never()).findByEmail(anyString());
    }

    @Test
    void anUnusedEnteredEmailIsAvailableInItsNormalizedForm() {
        SocialEmailAccountResolver.EnteredEmail entered =
                resolver.classifyEnteredEmail("  Member@Example.COM ");

        assertThat(entered.status()).isEqualTo(EnteredEmailStatus.AVAILABLE);
        assertThat(entered.email()).isEqualTo("member@example.com");
        verify(userMapper).findByEmail("member@example.com");
    }

    /** 연결은 다음 단계라서 지금은 가입만 막고 안내를 구분한다. */
    @Test
    void anEnteredEmailOfAnActiveMemberIsReportedSeparatelyForTheLinkingNotice() {
        when(userMapper.findByEmail("member@example.com"))
                .thenReturn(user(17L, UserStatus.ACTIVE));

        assertThat(resolver.classifyEnteredEmail("member@example.com").status())
                .isEqualTo(EnteredEmailStatus.EXISTING_ACTIVE);
    }

    /** 나머지 상태는 한 가지 안내로 묶어 계정 상태를 자세히 노출하지 않는다. */
    @ParameterizedTest
    @EnumSource(value = UserStatus.class,
            names = {"INACTIVE", "WITHDRAWAL_PENDING", "SUSPENDED", "RESTRICTED", "DEACTIVATED"})
    void anEnteredEmailHeldByAnyOtherAccountStateIsSimplyUnavailable(UserStatus status) {
        when(userMapper.findByEmail("member@example.com")).thenReturn(user(17L, status));

        assertThat(resolver.classifyEnteredEmail("member@example.com").status())
                .isEqualTo(EnteredEmailStatus.UNAVAILABLE);
    }

    /** 본인 확인이 없는 경로라 탈퇴 유예 계정을 여기서 파기하지 않는다. */
    @Test
    void classifyingAnEnteredEmailNeverFinalizesAWithdrawal() {
        when(userMapper.findByEmail("member@example.com"))
                .thenReturn(user(17L, UserStatus.WITHDRAWAL_PENDING));

        assertThat(resolver.classifyEnteredEmail("member@example.com").status())
                .isEqualTo(EnteredEmailStatus.UNAVAILABLE);
        verifyNoInteractions(withdrawalGraceService);
    }

    private User user(Long id, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        return user;
    }
}
