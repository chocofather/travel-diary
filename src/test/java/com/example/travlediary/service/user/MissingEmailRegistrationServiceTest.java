package com.example.travlediary.service.user;

import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailVerificationService;
import com.example.travlediary.service.user.MissingEmailRegistrationService.EmailAvailability;
import com.example.travlediary.service.user.MissingEmailRegistrationService.Outcome;
import com.example.travlediary.service.user.MissingEmailRegistrationService.Result;
import com.example.travlediary.service.user.SocialEmailAccountResolver.EnteredEmail;
import com.example.travlediary.service.user.SocialEmailAccountResolver.EnteredEmailStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissingEmailRegistrationServiceTest {

    private static final String EMAIL = "member@example.com";
    private static final String TOKEN = "verification-token";
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 9, 13, 1, 0);
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 9, 12, 1, 0);

    @Mock
    private UserMapper userMapper;
    @Mock
    private SocialEmailAccountResolver socialEmailAccountResolver;
    @Mock
    private EmailVerificationService emailVerificationService;

    private MissingEmailRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new MissingEmailRegistrationService(
                userMapper, socialEmailAccountResolver, emailVerificationService);
    }

    /** 대상 판정은 세션이 아니라 DB 한 번의 조회로 한다. */
    @Test
    void theTargetCheckAsksTheDatabaseEveryTime() {
        when(userMapper.isSocialAccountMissingEmail(7L)).thenReturn(true);

        assertThat(service.requiresEmailRegistration(7L)).isTrue();
        assertThat(service.requiresEmailRegistration(null)).isFalse();
        verify(userMapper).isSocialAccountMissingEmail(7L);
    }

    @Test
    void aUserWhoAlreadyHasAnEmailOrIsNotASocialLegacyAccountIsNotATarget() {
        when(userMapper.isSocialAccountMissingEmail(7L)).thenReturn(false);

        assertThat(service.requiresEmailRegistration(7L)).isFalse();
    }

    /** 다른 회원의 계정 상태는 UNAVAILABLE 뒤로 감춘다. */
    @ParameterizedTest
    @EnumSource(value = EnteredEmailStatus.class, names = {"EXISTING_ACTIVE", "UNAVAILABLE"})
    void everyKindOfTakenEmailLooksTheSameOnThisScreen(EnteredEmailStatus status) {
        when(socialEmailAccountResolver.classifyEnteredEmail(EMAIL))
                .thenReturn(new EnteredEmail(status, EMAIL));

        assertThat(service.checkAvailability(EMAIL)).isEqualTo(EmailAvailability.UNAVAILABLE);
    }

    @Test
    void anUnusedEmailIsAvailableAndAMalformedOneIsInvalid() {
        when(socialEmailAccountResolver.classifyEnteredEmail(EMAIL))
                .thenReturn(new EnteredEmail(EnteredEmailStatus.AVAILABLE, EMAIL));
        when(socialEmailAccountResolver.classifyEnteredEmail("nope"))
                .thenReturn(new EnteredEmail(EnteredEmailStatus.INVALID, null));

        assertThat(service.checkAvailability(EMAIL)).isEqualTo(EmailAvailability.AVAILABLE);
        assertThat(service.checkAvailability("nope")).isEqualTo(EmailAvailability.INVALID);
    }

    /** 같은 users 행을 인증 대기로 되돌린다. 새 계정을 만들지 않는다. */
    @Test
    void startingRegistrationUpdatesTheSameRowIntoEmailVerificationPending() {
        arrangeAvailable(" Member@Example.COM ", EMAIL);
        arrangeIssuedToken();
        when(userMapper.startEmailVerificationForSocialAccount(
                7L, EMAIL, TOKEN, EXPIRES_AT, REQUESTED_AT)).thenReturn(1);
        when(emailVerificationService.requestInitialVerification(any())).thenReturn(true);

        Outcome outcome = service.start(7L, " Member@Example.COM ");

        assertThat(outcome.result()).isEqualTo(Result.STARTED);
        assertThat(outcome.result().started()).isTrue();
        assertThat(outcome.userEmail()).isEqualTo(EMAIL);
        // 인증메일은 신규 가입과 같은 서비스가 보낸다.
        ArgumentCaptor<User> mailed = ArgumentCaptor.forClass(User.class);
        verify(emailVerificationService).requestInitialVerification(mailed.capture());
        assertThat(mailed.getValue().getId()).isEqualTo(7L);
        assertThat(mailed.getValue().getUserEmail()).isEqualTo(EMAIL);
        assertThat(mailed.getValue().getVerificationToken()).isEqualTo(TOKEN);
        // 새 users 를 만들지 않고 닉네임 같은 다른 컬럼도 건드리지 않는다.
        verify(userMapper, never()).insertUser(any());
        verify(userMapper, never()).updateAccountDetails(anyLong(), anyString(), anyString());
    }

    /** 메일 발송이 실패해도 등록은 되돌리지 않는다. */
    @Test
    void aFailedVerificationMailStillLeavesTheAccountWaiting() {
        arrangeAvailable(EMAIL, EMAIL);
        arrangeIssuedToken();
        when(userMapper.startEmailVerificationForSocialAccount(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(1);
        when(emailVerificationService.requestInitialVerification(any())).thenReturn(false);

        Outcome outcome = service.start(7L, EMAIL);

        assertThat(outcome.result()).isEqualTo(Result.STARTED_WITHOUT_MAIL);
        assertThat(outcome.result().started()).isTrue();
        assertThat(outcome.userEmail()).isEqualTo(EMAIL);
    }

    /** 이미 다른 회원이 쓰는 이메일이면 등록하지 않는다. 병합도 연결도 하지 않는다. */
    @Test
    void anEmailOwnedByAnotherMemberIsRejectedWithoutTouchingEitherAccount() {
        when(socialEmailAccountResolver.classifyEnteredEmail(EMAIL))
                .thenReturn(new EnteredEmail(EnteredEmailStatus.EXISTING_ACTIVE, EMAIL));

        assertThat(service.start(7L, EMAIL).result()).isEqualTo(Result.EMAIL_TAKEN);

        verify(userMapper, never()).startEmailVerificationForSocialAccount(
                anyLong(), anyString(), anyString(), any(), any());
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void aMalformedEmailIsRejectedBeforeAnyUpdate() {
        when(socialEmailAccountResolver.classifyEnteredEmail("nope"))
                .thenReturn(new EnteredEmail(EnteredEmailStatus.INVALID, null));

        assertThat(service.start(7L, "nope").result()).isEqualTo(Result.INVALID_EMAIL);
        verify(userMapper, never()).startEmailVerificationForSocialAccount(
                anyLong(), anyString(), anyString(), any(), any());
    }

    /** 화면을 열어 둔 사이 탈퇴·제재 등으로 상태가 바뀌면 UPDATE 가 0행이 된다. */
    @Test
    void anAccountThatIsNoLongerEligibleIsLeftUntouched() {
        arrangeAvailable(EMAIL, EMAIL);
        arrangeIssuedToken();
        when(userMapper.startEmailVerificationForSocialAccount(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(0);

        assertThat(service.start(7L, EMAIL).result()).isEqualTo(Result.NOT_ELIGIBLE);
        assertThat(Result.NOT_ELIGIBLE.started()).isFalse();
        verify(emailVerificationService, never()).requestInitialVerification(any());
    }

    /** 확인과 등록 사이의 UNIQUE 경합은 500 이 아니라 "이미 사용 중" 으로 다룬다. */
    @Test
    void aUniqueRaceAtUpdateTimeIsReportedAsAnAlreadyUsedEmail() {
        arrangeAvailable(EMAIL, EMAIL);
        arrangeIssuedToken();
        when(userMapper.startEmailVerificationForSocialAccount(
                anyLong(), anyString(), anyString(), any(), any()))
                .thenThrow(new DuplicateKeyException("user_email_UNIQUE"));

        Outcome outcome = service.start(7L, EMAIL);

        assertThat(outcome.result()).isEqualTo(Result.EMAIL_TAKEN);
        assertThat(outcome.userEmail()).isNull();
        verify(emailVerificationService, never()).requestInitialVerification(any());
    }

    @Test
    void anUnauthenticatedCallCannotStartAnything() {
        assertThat(service.start(null, EMAIL).result()).isEqualTo(Result.NOT_ELIGIBLE);
        verify(userMapper, never()).startEmailVerificationForSocialAccount(
                anyLong(), anyString(), anyString(), any(), any());
    }

    private void arrangeAvailable(String entered, String normalized) {
        when(socialEmailAccountResolver.classifyEnteredEmail(entered))
                .thenReturn(new EnteredEmail(EnteredEmailStatus.AVAILABLE, normalized));
    }

    /** 토큰 발급은 신규 가입과 같은 서비스가 맡는다. 여기서는 값만 흉내 낸다. */
    private void arrangeIssuedToken() {
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setVerificationToken(TOKEN);
            user.setVerificationTokenExp(EXPIRES_AT);
            user.setVerificationRequestedAt(REQUESTED_AT);
            return null;
        }).when(emailVerificationService).initializeVerification(any());
    }
}
