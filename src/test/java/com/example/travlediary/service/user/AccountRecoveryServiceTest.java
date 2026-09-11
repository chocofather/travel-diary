package com.example.travlediary.service.user;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.AccountRecoveryToken;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.AccountRecoveryTokenMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailDispatchService;
import com.example.travlediary.service.user.AccountRecoveryService.RecoveryRequestOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 탈퇴 유예 계정 복구. 상태를 되돌리는 기능이라 링크 수명과 유예기간의 관계,
 * 일회성 보장, 계정 존재 여부 비노출을 회귀 테스트로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountRecoveryServiceTest {

    private static final String SERVER_URL = "https://travel-diary.example";
    private static final String RECOVERY_PATH = "/users/recover-account/confirm?token=";
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 10, 0, 0);

    @Mock private UserMapper userMapper;
    @Mock private AccountRecoveryTokenMapper accountRecoveryTokenMapper;
    @Mock private EmailDispatchService emailDispatchService;

    private AccountRecoveryService accountRecoveryService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(ZonedDateTime.of(NOW, ZONE).toInstant(), ZONE);
        accountRecoveryService = new AccountRecoveryService(
                userMapper, accountRecoveryTokenMapper, emailDispatchService, clock);
        ReflectionTestUtils.setField(accountRecoveryService, "serverUrl", SERVER_URL);
    }

    /* ---------- 1. 복구 링크 발급 ---------- */

    @Test
    void recoveryRequestStoresOnlyTheHashAndPutsTheRawTokenInTheLinkAlone() {
        givenRecoverableAccount(7L, "member@gmail.com", NOW.plusDays(30));

        assertThat(accountRecoveryService.requestRecoveryFor(7L))
                .isEqualTo(RecoveryRequestOutcome.SENT);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountRecoveryTokenMapper).insertToken(
                eq(7L), hashCaptor.capture(), any(LocalDateTime.class));
        String storedHash = hashCaptor.getValue();
        assertThat(storedHash).matches("[0-9a-f]{64}");

        String rawToken = capturedRawToken();
        assertThat(rawToken).isNotBlank().isNotEqualTo(storedHash);
        assertThat(ResetTokenHasher.hash(rawToken)).isEqualTo(storedHash);
        // 원문은 DB 인자로 절대 넘어가지 않는다.
        verify(accountRecoveryTokenMapper, never()).insertToken(
                anyLong(), eq(rawToken), any(LocalDateTime.class));
    }

    @Test
    void recoveryLinkPointsAtTheConfirmEndpoint() {
        givenRecoverableAccount(7L, "member@gmail.com", NOW.plusDays(30));

        accountRecoveryService.requestRecoveryFor(7L);

        assertThat(capturedRecoveryUrl()).startsWith(SERVER_URL + RECOVERY_PATH);
    }

    /** 이메일은 다시 입력받지 않는다. 인증된 회원의 가입 주소를 서버가 찾아 쓴다. */
    @Test
    void theLinkGoesToTheStoredEmailOfTheAuthenticatedMember() {
        givenRecoverableAccount(7L, "member@gmail.com", NOW.plusDays(30));

        accountRecoveryService.requestRecoveryFor(7L);

        verify(userMapper).findRecoverableWithdrawalById(7L, NOW);
        verify(emailDispatchService).dispatchAccountRecoveryEmail(
                eq("member@gmail.com"), anyString(), anyLong(), any(SupportedLanguage.class));
    }

    /* ---------- 2. 복구 대상이 아닌 요청 ---------- */

    @Test
    void aRequestForAnAccountThatIsNotRecoverableIssuesNothingAndSendsNothing() {
        // ACTIVE / 이미 파기 예정이 지난 계정은 조회 단계에서 걸러진다.
        when(userMapper.findRecoverableWithdrawalById(anyLong(), any(LocalDateTime.class)))
                .thenReturn(null);

        assertThat(accountRecoveryService.requestRecoveryFor(7L))
                .isEqualTo(RecoveryRequestOutcome.NOT_ELIGIBLE);

        verify(accountRecoveryTokenMapper, never()).insertToken(
                anyLong(), anyString(), any(LocalDateTime.class));
        verifyNoInteractions(emailDispatchService);
    }

    @Test
    void aRequestWithoutAnAuthenticatedMemberIsRejectedBeforeAnyLookup() {
        assertThat(accountRecoveryService.requestRecoveryFor(null))
                .isEqualTo(RecoveryRequestOutcome.NOT_ELIGIBLE);

        verifyNoInteractions(userMapper, accountRecoveryTokenMapper, emailDispatchService);
    }

    /** 복구 대상 판단은 Java 가 아니라 조회 조건이 맡는다. 현재 시각이 그대로 넘어가야 한다. */
    @Test
    void recoverableLookupReceivesTheCurrentTimeSoExpiredGracePeriodsAreExcludedInSql() {
        when(userMapper.findRecoverableWithdrawalById(anyLong(), any(LocalDateTime.class)))
                .thenReturn(null);

        accountRecoveryService.requestRecoveryFor(7L);

        verify(userMapper).findRecoverableWithdrawalById(7L, NOW);
    }

    /* ---------- 3~4. 링크 유효시간과 유예기간의 관계 ---------- */

    @Test
    void recoveryLinkExpiresInThirtyMinutesWhenTheGracePeriodIsFarAway() {
        givenRecoverableAccount(7L, "member@gmail.com", NOW.plusDays(30));

        accountRecoveryService.requestRecoveryFor(7L);

        assertThat(capturedExpiry()).isEqualTo(NOW.plusMinutes(30));
        verify(emailDispatchService).dispatchAccountRecoveryEmail(
                eq("member@gmail.com"), anyString(), eq(30L), any(SupportedLanguage.class));
    }

    /** 최종 파기까지 8분 남았다면 새로 받은 링크도 8분만 유효하다. */
    @Test
    void recoveryLinkNeverOutlivesTheAccountsPurgeSchedule() {
        LocalDateTime purgeScheduledAt = NOW.plusMinutes(8);
        givenRecoverableAccount(7L, "member@gmail.com", purgeScheduledAt);

        accountRecoveryService.requestRecoveryFor(7L);

        assertThat(capturedExpiry()).isEqualTo(purgeScheduledAt);
        verify(emailDispatchService).dispatchAccountRecoveryEmail(
                eq("member@gmail.com"), anyString(), eq(8L), any(SupportedLanguage.class));
    }

    /** 복구 메일을 다시 받아도 30일 유예기간 자체는 연장되지 않는다. */
    @Test
    void requestingANewLinkNeverExtendsTheGracePeriod() {
        givenRecoverableAccount(7L, "member@gmail.com", NOW.plusMinutes(8));

        accountRecoveryService.requestRecoveryFor(7L);

        verify(userMapper).findRecoverableWithdrawalById(7L, NOW);
        verifyNoMoreInteractions(userMapper);
    }

    /* ---------- 5~6. 재발급 / 남용 방지 ---------- */

    @Test
    void issuingANewLinkClosesEveryUnusedLinkOfTheSameAccountFirst() {
        givenRecoverableAccount(7L, "member@gmail.com", NOW.plusDays(30));

        accountRecoveryService.requestRecoveryFor(7L);

        InOrder order = inOrder(accountRecoveryTokenMapper);
        order.verify(accountRecoveryTokenMapper).invalidateUnusedTokens(7L, NOW);
        order.verify(accountRecoveryTokenMapper).insertToken(
                eq(7L), anyString(), any(LocalDateTime.class));
    }

    @Test
    void backToBackRequestsAreThrottled() {
        givenRecoverableAccount(7L, "member@gmail.com", NOW.plusDays(30));
        when(accountRecoveryTokenMapper.findLatestIssuedAt(7L)).thenReturn(NOW.minusSeconds(10));

        assertThat(accountRecoveryService.requestRecoveryFor(7L))
                .isEqualTo(RecoveryRequestOutcome.COOLDOWN);

        verify(accountRecoveryTokenMapper, never()).insertToken(
                anyLong(), anyString(), any(LocalDateTime.class));
        verify(accountRecoveryTokenMapper, never()).invalidateUnusedTokens(
                anyLong(), any(LocalDateTime.class));
        verifyNoInteractions(emailDispatchService);
    }

    @Test
    void aRequestIsAcceptedAgainOnceTheCooldownHasPassed() {
        givenRecoverableAccount(7L, "member@gmail.com", NOW.plusDays(30));
        when(accountRecoveryTokenMapper.findLatestIssuedAt(7L)).thenReturn(NOW.minusSeconds(61));

        assertThat(accountRecoveryService.requestRecoveryFor(7L))
                .isEqualTo(RecoveryRequestOutcome.SENT);

        verify(accountRecoveryTokenMapper).insertToken(
                eq(7L), anyString(), any(LocalDateTime.class));
    }

    /* ---------- 링크 확인(GET) 은 아무것도 바꾸지 않는다 ---------- */

    @Test
    void checkingALinkNeverBurnsTheTokenOrTouchesTheAccount() {
        String rawToken = "raw-recovery-token";
        givenUsableToken(rawToken, 11L, 7L);
        givenReadableRecoverableAccount(7L);

        assertThat(accountRecoveryService.isRecoveryLinkUsable(rawToken)).isTrue();

        verify(accountRecoveryTokenMapper, never()).markUsed(anyLong(), any(LocalDateTime.class));
        verify(accountRecoveryTokenMapper, never()).invalidateUnusedTokens(
                anyLong(), any(LocalDateTime.class));
        verify(userMapper, never()).restoreWithdrawalPendingAccount(
                anyLong(), any(UserStatus.class), any(LocalDateTime.class));
        // 읽기 전용 확인이라 회원 행을 잠그지도 않는다.
        verify(userMapper, never()).findRecoverableWithdrawalByIdForUpdate(
                anyLong(), any(LocalDateTime.class));
        verify(userMapper).findRecoverableWithdrawalById(7L, NOW);
    }

    /** 메일 스캐너가 링크를 여러 번 열어도 계정은 그대로다. */
    @Test
    void repeatedAutomatedVisitsNeitherRecoverTheAccountNorConsumeTheToken() {
        String rawToken = "raw-recovery-token";
        givenUsableToken(rawToken, 11L, 7L);
        givenReadableRecoverableAccount(7L);

        for (int visit = 0; visit < 5; visit++) {
            assertThat(accountRecoveryService.isRecoveryLinkUsable(rawToken)).isTrue();
        }

        verify(accountRecoveryTokenMapper, never()).markUsed(anyLong(), any(LocalDateTime.class));
        verify(userMapper, never()).restoreWithdrawalPendingAccount(
                anyLong(), any(UserStatus.class), any(LocalDateTime.class));
    }

    @Test
    void anUnknownUsedOrExpiredLinkIsNotUsable() {
        when(accountRecoveryTokenMapper.findUsableByTokenHash(anyString(), any(LocalDateTime.class)))
                .thenReturn(null);

        assertThat(accountRecoveryService.isRecoveryLinkUsable("already-used-or-expired"))
                .isFalse();

        verifyNoInteractions(userMapper);
    }

    /** 링크가 살아 있어도 유예기간이 끝났으면 확인 화면을 보여주지 않는다. */
    @Test
    void aLinkOfAnAccountPastItsPurgeScheduleIsNotUsable() {
        String rawToken = "raw-recovery-token";
        givenUsableToken(rawToken, 11L, 7L);
        when(userMapper.findRecoverableWithdrawalById(7L, NOW)).thenReturn(null);

        assertThat(accountRecoveryService.isRecoveryLinkUsable(rawToken)).isFalse();
    }

    @ValueSource(strings = {"", "   "})
    @ParameterizedTest
    void blankTokensAreNotUsableAndTriggerNoLookup(String rawToken) {
        assertThat(accountRecoveryService.isRecoveryLinkUsable(rawToken)).isFalse();

        verifyNoInteractions(accountRecoveryTokenMapper, userMapper);
    }

    /* ---------- 6~8. 복구 확정 ---------- */

    @Test
    void aValidLinkRestoresTheAccountAndBurnsTheToken() {
        String rawToken = "raw-recovery-token";
        givenUsableToken(rawToken, 11L, 7L);
        givenLockedRecoverableAccount(7L);
        when(accountRecoveryTokenMapper.markUsed(11L, NOW)).thenReturn(1);
        when(userMapper.restoreWithdrawalPendingAccount(7L, UserStatus.ACTIVE, NOW)).thenReturn(1);

        assertThat(accountRecoveryService.confirmRecovery(rawToken)).isTrue();

        InOrder order = inOrder(accountRecoveryTokenMapper, userMapper);
        order.verify(accountRecoveryTokenMapper).markUsed(11L, NOW);
        order.verify(userMapper).restoreWithdrawalPendingAccount(7L, UserStatus.ACTIVE, NOW);
    }

    /**
     * 상태와 유예 일정만 되돌린다. 이메일/닉네임/비밀번호/소셜 연결/콘텐츠를 만지는 경로는
     * 이 흐름에 아예 없어야 한다.
     */
    @Test
    void recoveryTouchesNothingButTheAccountStatusAndScheduleColumns() {
        String rawToken = "raw-recovery-token";
        givenUsableToken(rawToken, 11L, 7L);
        givenLockedRecoverableAccount(7L);
        when(accountRecoveryTokenMapper.markUsed(11L, NOW)).thenReturn(1);
        when(userMapper.restoreWithdrawalPendingAccount(7L, UserStatus.ACTIVE, NOW)).thenReturn(1);

        accountRecoveryService.confirmRecovery(rawToken);

        verify(userMapper).findRecoverableWithdrawalByIdForUpdate(7L, NOW);
        verify(userMapper).restoreWithdrawalPendingAccount(7L, UserStatus.ACTIVE, NOW);
        verifyNoMoreInteractions(userMapper);
    }

    /** 소셜 회원도 같은 링크 흐름을 쓴다. 아이디/비밀번호가 없어도 달라지는 것이 없다. */
    @Test
    void socialOnlyMembersAreRecoveredThroughTheSameEmailFlow() {
        String rawToken = "raw-recovery-token";
        User socialMember = new User();
        socialMember.setId(9L);
        socialMember.setUserEmail("social@gmail.com");
        socialMember.setUsername(null);
        socialMember.setUserPassword(null);
        socialMember.setStatus(UserStatus.WITHDRAWAL_PENDING);
        socialMember.setPurgeScheduledAt(NOW.plusDays(30));
        when(userMapper.findRecoverableWithdrawalById(9L, NOW)).thenReturn(socialMember);
        accountRecoveryService.requestRecoveryFor(9L);
        verify(accountRecoveryTokenMapper).insertToken(
                eq(9L), anyString(), eq(NOW.plusMinutes(30)));

        givenUsableToken(rawToken, 12L, 9L);
        when(userMapper.findRecoverableWithdrawalByIdForUpdate(9L, NOW)).thenReturn(socialMember);
        when(accountRecoveryTokenMapper.markUsed(12L, NOW)).thenReturn(1);
        when(userMapper.restoreWithdrawalPendingAccount(9L, UserStatus.ACTIVE, NOW)).thenReturn(1);

        assertThat(accountRecoveryService.confirmRecovery(rawToken)).isTrue();
        // 소셜 연결을 다시 잇는 별도 처리는 없다.
        verify(userMapper).restoreWithdrawalPendingAccount(9L, UserStatus.ACTIVE, NOW);
    }

    /** 이미 쓴 링크, 만료된 링크, 존재하지 않는 링크는 모두 조회 단계에서 걸러진다. */
    @Test
    void usedOrExpiredOrUnknownLinksAreRejected() {
        when(accountRecoveryTokenMapper.findUsableByTokenHash(anyString(), any(LocalDateTime.class)))
                .thenReturn(null);

        assertThat(accountRecoveryService.confirmRecovery("already-used-or-expired")).isFalse();

        verify(accountRecoveryTokenMapper).findUsableByTokenHash(
                ResetTokenHasher.hash("already-used-or-expired"), NOW);
        verifyNoInteractions(userMapper);
    }

    @ValueSource(strings = {"", "   "})
    @ParameterizedTest
    void blankTokensAreRejectedWithoutAnyLookup(String rawToken) {
        assertThat(accountRecoveryService.confirmRecovery(rawToken)).isFalse();

        verifyNoInteractions(accountRecoveryTokenMapper, userMapper);
    }

    /** 같은 링크로 동시에 들어와도 used_at IS NULL 조건 때문에 한 번만 성공한다. */
    @Test
    void aTokenThatIsBurnedConcurrentlyDoesNotRestoreTheAccountTwice() {
        String rawToken = "raw-recovery-token";
        givenUsableToken(rawToken, 11L, 7L);
        givenLockedRecoverableAccount(7L);
        when(accountRecoveryTokenMapper.markUsed(11L, NOW)).thenReturn(0);

        assertThat(accountRecoveryService.confirmRecovery(rawToken)).isFalse();

        verify(userMapper, never()).restoreWithdrawalPendingAccount(
                anyLong(), any(UserStatus.class), any(LocalDateTime.class));
    }

    /* ---------- 9. 유예기간이 끝난 계정 ---------- */

    @Test
    void aLiveLinkStillFailsOnceThePurgeScheduleHasPassed() {
        String rawToken = "raw-recovery-token";
        givenUsableToken(rawToken, 11L, 7L);
        // 데이터가 아직 남아 있어도 purge_scheduled_at 조건 때문에 잠금 조회가 비어 온다.
        when(userMapper.findRecoverableWithdrawalByIdForUpdate(7L, NOW)).thenReturn(null);

        assertThat(accountRecoveryService.confirmRecovery(rawToken)).isFalse();

        verify(accountRecoveryTokenMapper, never()).markUsed(anyLong(), any(LocalDateTime.class));
        verify(userMapper, never()).restoreWithdrawalPendingAccount(
                anyLong(), any(UserStatus.class), any(LocalDateTime.class));
    }

    /** 확인 화면을 본 것과 실제로 복구할 수 있는 것은 별개다. POST 에서 전부 다시 본다. */
    @Test
    void aLinkThatExpiresBetweenTheCheckAndTheSubmitIsRejectedOnSubmit() {
        String rawToken = "raw-recovery-token";
        givenUsableToken(rawToken, 11L, 7L);
        givenReadableRecoverableAccount(7L);
        assertThat(accountRecoveryService.isRecoveryLinkUsable(rawToken)).isTrue();

        // 제출 시점에는 expires_at 조건 때문에 토큰 조회가 비어 온다.
        when(accountRecoveryTokenMapper.findUsableByTokenHash(
                ResetTokenHasher.hash(rawToken), NOW)).thenReturn(null);

        assertThat(accountRecoveryService.confirmRecovery(rawToken)).isFalse();
        verify(userMapper, never()).restoreWithdrawalPendingAccount(
                anyLong(), any(UserStatus.class), any(LocalDateTime.class));
    }

    @Test
    void anAccountWhosePurgeScheduleLapsesBetweenTheCheckAndTheSubmitIsRejectedOnSubmit() {
        String rawToken = "raw-recovery-token";
        givenUsableToken(rawToken, 11L, 7L);
        givenReadableRecoverableAccount(7L);
        assertThat(accountRecoveryService.isRecoveryLinkUsable(rawToken)).isTrue();

        // 제출 시점에는 purge_scheduled_at 조건 때문에 잠금 조회가 비어 온다.
        when(userMapper.findRecoverableWithdrawalByIdForUpdate(7L, NOW)).thenReturn(null);

        assertThat(accountRecoveryService.confirmRecovery(rawToken)).isFalse();
        verify(accountRecoveryTokenMapper, never()).markUsed(anyLong(), any(LocalDateTime.class));
        verify(userMapper, never()).restoreWithdrawalPendingAccount(
                anyLong(), any(UserStatus.class), any(LocalDateTime.class));
    }

    /** 잠근 뒤에도 갱신되지 않으면 토큰 사용 처리까지 롤백되도록 트랜잭션을 깬다. */
    @Test
    void aFailedRestoreRollsBackInsteadOfBurningTheToken() {
        String rawToken = "raw-recovery-token";
        givenUsableToken(rawToken, 11L, 7L);
        givenLockedRecoverableAccount(7L);
        when(accountRecoveryTokenMapper.markUsed(11L, NOW)).thenReturn(1);
        when(userMapper.restoreWithdrawalPendingAccount(7L, UserStatus.ACTIVE, NOW)).thenReturn(0);

        assertThatThrownBy(() -> accountRecoveryService.confirmRecovery(rawToken))
                .isInstanceOf(IllegalStateException.class);
    }

    /* ---------- 12. 메일 언어 ---------- */

    /**
     * 메일 발송은 @Async 라 워커 스레드에서 locale 을 다시 읽을 수 없다.
     * 요청 스레드의 언어가 인자로 그대로 전달되어야 한다.
     */
    @ParameterizedTest
    @CsvSource({
            "ko, KOREAN", "en, ENGLISH", "ja, JAPANESE",
            "zh-CN, CHINESE_SIMPLIFIED", "zh-TW, CHINESE_TRADITIONAL", "fr, KOREAN"
    })
    void theRecoveryMailCarriesTheRequestLanguage(String languageTag, SupportedLanguage expected) {
        givenRecoverableAccount(7L, "member@gmail.com", NOW.plusDays(30));

        LocaleContextHolder.setLocale(Locale.forLanguageTag(languageTag));
        try {
            accountRecoveryService.requestRecoveryFor(7L);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }

        verify(emailDispatchService).dispatchAccountRecoveryEmail(
                eq("member@gmail.com"), anyString(), eq(30L), eq(expected));
    }

    /* ---------- helpers ---------- */

    private void givenRecoverableAccount(Long id, String email, LocalDateTime purgeScheduledAt) {
        User account = new User();
        account.setId(id);
        account.setUserEmail(email);
        account.setStatus(UserStatus.WITHDRAWAL_PENDING);
        account.setPurgeScheduledAt(purgeScheduledAt);
        when(userMapper.findRecoverableWithdrawalById(id, NOW)).thenReturn(account);
    }

    private void givenReadableRecoverableAccount(Long id) {
        User account = new User();
        account.setId(id);
        account.setStatus(UserStatus.WITHDRAWAL_PENDING);
        account.setPurgeScheduledAt(NOW.plusDays(10));
        when(userMapper.findRecoverableWithdrawalById(id, NOW)).thenReturn(account);
    }

    private void givenLockedRecoverableAccount(Long id) {
        User account = new User();
        account.setId(id);
        account.setStatus(UserStatus.WITHDRAWAL_PENDING);
        account.setPurgeScheduledAt(NOW.plusDays(10));
        when(userMapper.findRecoverableWithdrawalByIdForUpdate(id, NOW)).thenReturn(account);
    }

    private void givenUsableToken(String rawToken, Long tokenId, Long userId) {
        AccountRecoveryToken token = new AccountRecoveryToken();
        token.setId(tokenId);
        token.setUserId(userId);
        token.setExpiresAt(NOW.plusMinutes(5));
        when(accountRecoveryTokenMapper.findUsableByTokenHash(
                ResetTokenHasher.hash(rawToken), NOW)).thenReturn(token);
    }

    private LocalDateTime capturedExpiry() {
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(accountRecoveryTokenMapper).insertToken(anyLong(), anyString(), captor.capture());
        return captor.getValue();
    }

    private String capturedRecoveryUrl() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailDispatchService).dispatchAccountRecoveryEmail(
                anyString(), captor.capture(), anyLong(), any(SupportedLanguage.class));
        return captor.getValue();
    }

    private String capturedRawToken() {
        String url = capturedRecoveryUrl();
        return url.substring(url.indexOf(RECOVERY_PATH) + RECOVERY_PATH.length());
    }
}
