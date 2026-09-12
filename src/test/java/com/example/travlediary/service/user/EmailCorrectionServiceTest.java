package com.example.travlediary.service.user;

import com.example.travlediary.model.PendingEmailCorrection;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailVerificationService;
import com.example.travlediary.service.user.EmailCorrectionService.Outcome;
import com.example.travlediary.service.user.EmailCorrectionService.Result;
import com.example.travlediary.service.user.SocialEmailAccountResolver.EnteredEmail;
import com.example.travlediary.service.user.SocialEmailAccountResolver.EnteredEmailStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailCorrectionServiceTest {

    private static final String WRONG_EMAIL = "typo@example.com";
    private static final String NEW_EMAIL = "correct@example.com";
    private static final String TOKEN = "new-token";
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 9, 14, 1, 0);
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 9, 13, 1, 0);

    @Mock
    private UserMapper userMapper;
    @Mock
    private SocialAccountService socialAccountService;
    @Mock
    private SocialEmailAccountResolver socialEmailAccountResolver;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private EmailCorrectionService service;

    @BeforeEach
    void setUp() {
        service = new EmailCorrectionService(userMapper, socialAccountService,
                socialEmailAccountResolver, emailVerificationService, passwordEncoder);
    }

    // ---------- begin ----------

    @ParameterizedTest
    @EnumSource(value = SocialProvider.class, names = {"KAKAO", "NAVER"})
    void beginningACorrectionResolvesTheTargetOnTheServerWithoutGrantingAnything(
            SocialProvider provider) {
        arrangeTarget(provider);

        PendingEmailCorrection correction = service.beginSocial(WRONG_EMAIL, provider);

        assertThat(correction).isNotNull();
        assertThat(correction.targetUserId()).isEqualTo(23L);
        assertThat(correction.expectedCurrentEmail()).isEqualTo(WRONG_EMAIL);
        assertThat(correction.provider()).isEqualTo(provider);
        // 재인증 전에는 어떤 권한도 없다.
        assertThat(correction.authorized()).isFalse();
        assertThat(correction.isAuthorizedAt(Instant.now())).isFalse();
        assertThat(Duration.between(correction.createdAt(), correction.expiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
    }

    /** 그 계정에 연결되지 않은 provider 로는 시작할 수 없다. */
    @Test
    void aProviderThatIsNotConnectedToTheTargetCannotStartACorrection() {
        arrangeTarget(SocialProvider.KAKAO);

        assertThat(service.beginSocial(WRONG_EMAIL, SocialProvider.NAVER)).isNull();
        assertThat(service.beginSocial(WRONG_EMAIL, SocialProvider.GOOGLE)).isNull();
    }

    /** 인증 대기 상태가 아니면 시작할 수 없다. */
    @Test
    void anAccountThatIsNoLongerPendingVerificationCannotStartACorrection() {
        arrangeTarget(SocialProvider.KAKAO);
        when(userMapper.isEmailVerificationPending(23L)).thenReturn(false);

        assertThat(service.beginSocial(WRONG_EMAIL, SocialProvider.KAKAO)).isNull();
    }

    @Test
    void anUnknownOrMalformedPendingEmailCannotStartACorrection() {
        assertThat(service.beginSocial("nope", SocialProvider.KAKAO)).isNull();
        assertThat(service.beginSocial(null, SocialProvider.KAKAO)).isNull();
        assertThat(service.beginSocial(WRONG_EMAIL, SocialProvider.KAKAO)).isNull();
    }


    /** 대기 이메일만으로 판정한다. 세션을 보지 않으므로 새 세션에서도 같은 답이 나온다. */
    @Test
    void theCorrectionOptionsAreResolvedFromTheDatabaseByThePendingEmailAlone() {
        arrangeTarget(SocialProvider.NAVER);

        EmailCorrectionService.CorrectionOptions options =
                service.optionsFor(" Typo@Example.COM ");

        assertThat(options.available()).isTrue();
        assertThat(options.providers()).containsExactly(SocialProvider.NAVER);
        assertThat(options.passwordAvailable()).isFalse();
        verify(userMapper).findByEmail(WRONG_EMAIL);
        verify(userMapper).isEmailVerificationPending(23L);
    }

    /** 비밀번호가 있는 일반 회원은 비밀번호 확인이 후보로 나온다. */
    @Test
    void aLocalAccountOffersThePasswordCheck() {
        User target = arrangeTarget(SocialProvider.KAKAO);
        target.setUserPassword("{bcrypt}hash");

        EmailCorrectionService.CorrectionOptions options = service.optionsFor(WRONG_EMAIL);

        assertThat(options.passwordAvailable()).isTrue();
        assertThat(options.providers()).containsExactly(SocialProvider.KAKAO);
        assertThat(options.available()).isTrue();
    }

    /** 인증 대기가 아니면 어떤 수단도 열리지 않는다. */
    @Test
    void anAccountThatIsNotPendingVerificationOffersNothing() {
        arrangeTarget(SocialProvider.KAKAO);
        when(userMapper.isEmailVerificationPending(23L)).thenReturn(false);

        assertThat(service.optionsFor(WRONG_EMAIL).available()).isFalse();
    }

    @Test
    void anUnknownOrMalformedPendingEmailOffersNothing() {
        assertThat(service.optionsFor("nope").available()).isFalse();
        assertThat(service.optionsFor(null).available()).isFalse();
        assertThat(service.optionsFor("").available()).isFalse();
        // 형식이 틀리면 조회조차 하지 않는다.
        verify(userMapper, never()).findByEmail("nope");
        // 계정이 없으면 후보도 없다.
        assertThat(service.optionsFor(WRONG_EMAIL).available()).isFalse();
    }


    // ---------- LOCAL 본인확인 ----------

    /** 비밀번호가 있는 계정만 비밀번호 확인 경로를 시작할 수 있다. */
    @Test
    void aLocalCorrectionStartsUnauthorizedAndCarriesNoProvider() {
        User target = arrangeTarget(SocialProvider.KAKAO);
        target.setUserPassword("{bcrypt}hash");

        PendingEmailCorrection correction = service.beginLocal(WRONG_EMAIL);

        assertThat(correction).isNotNull();
        assertThat(correction.method()).isEqualTo(PendingEmailCorrection.Method.LOCAL);
        assertThat(correction.provider()).isNull();
        assertThat(correction.targetUserId()).isEqualTo(23L);
        assertThat(correction.expectedCurrentEmail()).isEqualTo(WRONG_EMAIL);
        // 비밀번호를 확인하기 전에는 권한이 없다.
        assertThat(correction.authorized()).isFalse();
        assertThat(Duration.between(correction.createdAt(), correction.expiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
    }

    /** 비밀번호가 없는 소셜 전용 계정은 비밀번호 경로를 쓸 수 없다. */
    @Test
    void aSocialOnlyAccountCannotUseThePasswordPath() {
        arrangeTarget(SocialProvider.KAKAO);

        assertThat(service.beginLocal(WRONG_EMAIL)).isNull();
        assertThat(service.beginLocal("nope")).isNull();
    }

    @Test
    void theCorrectPasswordGrantsTheCorrectionRightWithoutLoggingAnyoneIn() {
        User target = arrangeTarget(SocialProvider.KAKAO);
        target.setUserPassword("{bcrypt}hash");
        when(passwordEncoder.matches("secret", "{bcrypt}hash")).thenReturn(true);

        PendingEmailCorrection authorized =
                service.authorizeLocal(localPending(), "secret");

        assertThat(authorized).isNotNull();
        assertThat(authorized.authorized()).isTrue();
        assertThat(authorized.targetUserId()).isEqualTo(23L);
    }

    /** 틀린 비밀번호로는 권한이 생기지 않는다. 계정 정보도 흘리지 않는다. */
    @Test
    void aWrongOrMissingPasswordNeverGrantsTheRight() {
        User target = arrangeTarget(SocialProvider.KAKAO);
        target.setUserPassword("{bcrypt}hash");
        when(passwordEncoder.matches("wrong", "{bcrypt}hash")).thenReturn(false);

        assertThat(service.authorizeLocal(localPending(), "wrong")).isNull();
        assertThat(service.authorizeLocal(localPending(), "")).isNull();
        assertThat(service.authorizeLocal(localPending(), null)).isNull();
        assertThat(service.authorizeLocal(null, "secret")).isNull();
    }

    /** 대상이 더는 인증 대기가 아니면 비밀번호가 맞아도 권한이 없다. */
    @Test
    void aPasswordCheckOnAnAccountThatLeftTheWaitingStateFails() {
        User target = arrangeTarget(SocialProvider.KAKAO);
        target.setUserPassword("{bcrypt}hash");
        when(userMapper.isEmailVerificationPending(23L)).thenReturn(false);

        assertThat(service.authorizeLocal(localPending(), "secret")).isNull();
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    /** LOCAL 문맥을 소셜 재인증으로, SOCIAL 문맥을 비밀번호로 승인할 수 없다. */
    @Test
    void theTwoVerificationMethodsCannotBeSwapped() {
        User target = arrangeTarget(SocialProvider.KAKAO);
        target.setUserPassword("{bcrypt}hash");
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "kakao-sub")).thenReturn(socialAccount(23L));

        // LOCAL 문맥에는 소셜 재인증이 붙지 않는다.
        assertThat(service.authorize(
                localPending(), SocialProvider.KAKAO, "kakao-sub")).isNull();
        // SOCIAL 문맥에는 비밀번호가 붙지 않는다.
        assertThat(service.authorizeLocal(pending(SocialProvider.KAKAO), "secret")).isNull();
    }

    private PendingEmailCorrection localPending() {
        Instant now = Instant.now();
        return new PendingEmailCorrection("flow", 23L, WRONG_EMAIL,
                PendingEmailCorrection.Method.LOCAL, null, false,
                now.minusSeconds(10), now.plusSeconds(590));
    }

    // ---------- authorize ----------

    /** OAuth 성공만으로는 통과하지 못한다. 신원이 target 의 것이어야 한다. */
    @Test
    void reauthenticatingWithTheTargetOwnIdentityGrantsTheCorrectionRight() {
        arrangeTarget(SocialProvider.KAKAO);
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "kakao-sub")).thenReturn(socialAccount(23L));

        PendingEmailCorrection authorized = service.authorize(
                pending(SocialProvider.KAKAO), SocialProvider.KAKAO, "kakao-sub");

        assertThat(authorized).isNotNull();
        assertThat(authorized.authorized()).isTrue();
        assertThat(authorized.targetUserId()).isEqualTo(23L);
        assertThat(authorized.expectedCurrentEmail()).isEqualTo(WRONG_EMAIL);
    }

    /** 남의 Kakao/Naver 로 인증하면 절대 변경 권한을 주지 않는다. */
    @Test
    void anIdentityBelongingToAnotherMemberNeverGrantsTheRight() {
        arrangeTarget(SocialProvider.KAKAO);
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "someone-else")).thenReturn(socialAccount(99L));

        assertThat(service.authorize(
                pending(SocialProvider.KAKAO), SocialProvider.KAKAO, "someone-else")).isNull();
    }

    @Test
    void anUnknownIdentityADifferentProviderOrAnExpiredContextNeverGrantsTheRight() {
        arrangeTarget(SocialProvider.KAKAO);

        // social_accounts 에 없는 신원
        assertThat(service.authorize(
                pending(SocialProvider.KAKAO), SocialProvider.KAKAO, "unknown")).isNull();
        // 문맥이 정한 provider 와 다른 provider
        assertThat(service.authorize(
                pending(SocialProvider.KAKAO), SocialProvider.NAVER, "naver-id")).isNull();
        // 만료된 문맥
        Instant past = Instant.now().minusSeconds(700);
        assertThat(service.authorize(new PendingEmailCorrection(
                "flow", 23L, WRONG_EMAIL, PendingEmailCorrection.Method.SOCIAL, SocialProvider.KAKAO, false,
                past, past.plusSeconds(600)), SocialProvider.KAKAO, "kakao-sub")).isNull();
        assertThat(service.authorize(null, SocialProvider.KAKAO, "kakao-sub")).isNull();
    }

    // ---------- change ----------

    /** 같은 users 행의 이메일과 토큰만 바뀐다. */
    @Test
    void changingTheEmailReplacesTheAddressAndInvalidatesTheOldTokenOnTheSameRow() {
        arrangeAvailable(NEW_EMAIL);
        arrangeIssuedToken();
        when(userMapper.changePendingVerificationEmail(
                23L, NEW_EMAIL, WRONG_EMAIL, TOKEN, EXPIRES_AT, REQUESTED_AT)).thenReturn(1);
        when(emailVerificationService.requestInitialVerification(any())).thenReturn(true);

        Outcome outcome = service.change(authorized(SocialProvider.KAKAO), NEW_EMAIL);

        assertThat(outcome.result()).isEqualTo(Result.CHANGED);
        assertThat(outcome.userEmail()).isEqualTo(NEW_EMAIL);
        // 새 토큰이 예전 토큰을 덮어쓴다. 예전 링크는 그 즉시 무효다.
        verify(userMapper).changePendingVerificationEmail(
                23L, NEW_EMAIL, WRONG_EMAIL, TOKEN, EXPIRES_AT, REQUESTED_AT);
        verify(userMapper, never()).insertUser(any());
        verify(userMapper, never()).updateMyPageProfile(anyLong(), anyString(), anyString());
    }

    /** 권한 없는 문맥으로는 아무것도 바꾸지 못한다. */
    @Test
    void anUnauthorizedOrExpiredContextChangesNothing() {
        assertThat(service.change(pending(SocialProvider.KAKAO), NEW_EMAIL).result())
                .isEqualTo(Result.NOT_AUTHORIZED);
        assertThat(service.change(null, NEW_EMAIL).result()).isEqualTo(Result.NOT_AUTHORIZED);

        Instant past = Instant.now().minusSeconds(700);
        assertThat(service.change(new PendingEmailCorrection(
                "flow", 23L, WRONG_EMAIL, PendingEmailCorrection.Method.SOCIAL, SocialProvider.KAKAO, true,
                past, past.plusSeconds(600)), NEW_EMAIL).result())
                .isEqualTo(Result.NOT_AUTHORIZED);

        verify(userMapper, never()).changePendingVerificationEmail(
                anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    /**
     * 예전 링크가 먼저 사용돼 ACTIVE 가 됐거나 이메일이 달라졌으면 UPDATE 가 0행이다.
     * ACTIVE 계정의 이메일을 이 기능으로 바꾸지 않는다.
     */
    @Test
    void anAccountThatWasVerifiedOrChangedInTheMeantimeIsLeftAlone() {
        arrangeAvailable(NEW_EMAIL);
        arrangeIssuedToken();
        when(userMapper.changePendingVerificationEmail(
                anyLong(), anyString(), anyString(), anyString(), any(), any())).thenReturn(0);

        assertThat(service.change(authorized(SocialProvider.KAKAO), NEW_EMAIL).result())
                .isEqualTo(Result.NOT_AUTHORIZED);
        verify(emailVerificationService, never()).requestInitialVerification(any());
    }

    @Test
    void anEmailOwnedByAnotherMemberOrAMalformedOneIsRejected() {
        when(socialEmailAccountResolver.classifyEnteredEmail(NEW_EMAIL))
                .thenReturn(new EnteredEmail(EnteredEmailStatus.EXISTING_ACTIVE, NEW_EMAIL));
        when(socialEmailAccountResolver.classifyEnteredEmail("nope"))
                .thenReturn(new EnteredEmail(EnteredEmailStatus.INVALID, null));

        assertThat(service.change(authorized(SocialProvider.KAKAO), NEW_EMAIL).result())
                .isEqualTo(Result.EMAIL_TAKEN);
        assertThat(service.change(authorized(SocialProvider.KAKAO), "nope").result())
                .isEqualTo(Result.INVALID_EMAIL);
        verify(userMapper, never()).changePendingVerificationEmail(
                anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    /** 확인과 저장 사이의 UNIQUE 경합은 "이미 사용 중" 으로 다룬다. */
    @Test
    void aUniqueRaceAtUpdateTimeIsReportedAsAnAlreadyUsedEmail() {
        arrangeAvailable(NEW_EMAIL);
        arrangeIssuedToken();
        when(userMapper.changePendingVerificationEmail(
                anyLong(), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new DuplicateKeyException("user_email_UNIQUE"));

        assertThat(service.change(authorized(SocialProvider.KAKAO), NEW_EMAIL).result())
                .isEqualTo(Result.EMAIL_TAKEN);
        verify(emailVerificationService, never()).requestInitialVerification(any());
    }

    /** 같은 주소로는 바꾸지 않는다. 멀쩡한 토큰을 헛되이 무효화하지 않는다. */
    @Test
    void theSameAddressIsRejectedWithoutInvalidatingTheExistingToken() {
        arrangeAvailable(WRONG_EMAIL);

        assertThat(service.change(authorized(SocialProvider.KAKAO), WRONG_EMAIL).result())
                .isEqualTo(Result.UNCHANGED);
        verify(userMapper, never()).changePendingVerificationEmail(
                anyLong(), anyString(), anyString(), anyString(), any(), any());
        verify(emailVerificationService, never()).initializeVerification(any());
    }

    @Test
    void aFailedVerificationMailStillCountsAsChanged() {
        arrangeAvailable(NEW_EMAIL);
        arrangeIssuedToken();
        when(userMapper.changePendingVerificationEmail(
                anyLong(), anyString(), anyString(), anyString(), any(), any())).thenReturn(1);
        when(emailVerificationService.requestInitialVerification(any())).thenReturn(false);

        Outcome outcome = service.change(authorized(SocialProvider.KAKAO), NEW_EMAIL);

        assertThat(outcome.result()).isEqualTo(Result.CHANGED_WITHOUT_MAIL);
        assertThat(outcome.result().changed()).isTrue();
    }

    /** 본인확인에 쓸 수 있는 provider 는 실제 연결된 Kakao/Naver 뿐이다. */
    @Test
    void onlyConnectedKakaoOrNaverAccountsAreOfferedForReauthentication() {
        when(socialAccountService.findAllByUserId(23L)).thenReturn(List.of(
                socialAccount(23L, SocialProvider.GOOGLE),
                socialAccount(23L, SocialProvider.NAVER)));

        assertThat(service.reauthenticationProviders(23L))
                .containsExactly(SocialProvider.NAVER);
        assertThat(service.reauthenticationProviders(null)).isEmpty();
    }

    private User arrangeTarget(SocialProvider provider) {
        User target = new User();
        target.setId(23L);
        target.setUserEmail(WRONG_EMAIL);
        target.setStatus(UserStatus.INACTIVE);
        when(userMapper.findByEmail(WRONG_EMAIL)).thenReturn(target);
        when(userMapper.findById(23L)).thenReturn(target);
        when(userMapper.isEmailVerificationPending(23L)).thenReturn(true);
        when(socialAccountService.findAllByUserId(23L))
                .thenReturn(List.of(socialAccount(23L, provider)));
        return target;
    }

    private void arrangeAvailable(String email) {
        when(userMapper.isEmailVerificationPending(23L)).thenReturn(true);
        when(socialEmailAccountResolver.classifyEnteredEmail(email))
                .thenReturn(new EnteredEmail(EnteredEmailStatus.AVAILABLE, email));
    }

    private void arrangeIssuedToken() {
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setVerificationToken(TOKEN);
            user.setVerificationTokenExp(EXPIRES_AT);
            user.setVerificationRequestedAt(REQUESTED_AT);
            return null;
        }).when(emailVerificationService).initializeVerification(any());
    }

    private PendingEmailCorrection pending(SocialProvider provider) {
        Instant now = Instant.now();
        return new PendingEmailCorrection("flow", 23L, WRONG_EMAIL,
                PendingEmailCorrection.Method.SOCIAL, provider, false,
                now.minusSeconds(10), now.plusSeconds(590));
    }

    private PendingEmailCorrection authorized(SocialProvider provider) {
        return pending(provider).authorize();
    }

    private SocialAccount socialAccount(Long userId) {
        return socialAccount(userId, SocialProvider.KAKAO);
    }

    private SocialAccount socialAccount(Long userId, SocialProvider provider) {
        SocialAccount account = new SocialAccount();
        account.setUserId(userId);
        account.setProvider(provider);
        return account;
    }
}
