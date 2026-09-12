package com.example.travlediary.service.user;

import com.example.travlediary.dto.SocialSignupForm;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.SocialAccountMapper;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialSignupServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private SocialAccountMapper socialAccountMapper;

    private SocialSignupService service;

    @BeforeEach
    void setUp() {
        service = new SocialSignupService(userMapper, socialAccountMapper);
    }

    @Test
    void completionIsOneTransactionalBoundary() throws Exception {
        assertThat(SocialSignupService.class
                .getMethod("complete", PendingSocialSignup.class, SocialSignupForm.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void createsActiveUserAndGoogleConnectionFromServerPendingOnly() {
        arrangeGeneratedUserId(41L);
        SocialSignupForm form = acceptedForm(" 여행자123 ");

        long userId = service.complete(pending("google-sub", "new@example.com", true), form);

        assertThat(userId).isEqualTo(41L);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insertUser(userCaptor.capture());
        User user = userCaptor.getValue();
        assertThat(user.getUsername()).isNull();
        assertThat(user.getUserPassword()).isNull();
        assertThat(user.getFullName()).isNull();
        assertThat(user.getUserBirth()).isNull();
        // Google 은 email_verified 를 함께 주므로 그 이메일이 곧 공식 이메일이다.
        assertThat(user.getUserEmail()).isEqualTo("new@example.com");
        assertThat(user.getNickname()).isEqualTo("여행자123");
        assertThat(user.getUserRole()).isEqualTo(UserRole.USER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getCreatedAt()).isNotNull();

        ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountMapper).insert(accountCaptor.capture());
        SocialAccount account = accountCaptor.getValue();
        assertThat(account.getUserId()).isEqualTo(41L);
        assertThat(account.getProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(account.getProviderUserId()).isEqualTo("google-sub");
        assertThat(account.getProviderEmail()).isEqualTo("new@example.com");
        assertThat(account.getProviderEmailVerified()).isTrue();
    }

    @Test
    void createsKakaoConnectionWithoutRequiringProviderEmail() {
        arrangeGeneratedUserId(52L);
        PendingSocialSignup pending = pending(
                SocialProvider.KAKAO, "kakao-sub", null, null);

        long userId = service.complete(pending, acceptedForm("카카오여행자"));

        assertThat(userId).isEqualTo(52L);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insertUser(userCaptor.capture());
        assertThat(userCaptor.getValue().getUserEmail()).isNull();

        ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountMapper).insert(accountCaptor.capture());
        SocialAccount account = accountCaptor.getValue();
        assertThat(account.getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(account.getProviderUserId()).isEqualTo("kakao-sub");
        assertThat(account.getProviderEmail()).isNull();
        assertThat(account.getProviderEmailVerified()).isNull();
        verify(socialAccountMapper).findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "kakao-sub");
    }

    @Test
    void keepsKakaoEmailOnlyAsSocialAccountReference() {
        arrangeGeneratedUserId(53L);
        PendingSocialSignup pending = pending(
                SocialProvider.KAKAO, "kakao-sub-with-email", "kakao@example.com", true);

        service.complete(pending, acceptedForm("카카오여행자"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insertUser(userCaptor.capture());
        assertThat(userCaptor.getValue().getUserEmail()).isNull();
        ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountMapper).insert(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getProviderEmail()).isEqualTo("kakao@example.com");
        assertThat(accountCaptor.getValue().getProviderEmailVerified()).isTrue();
        verify(userMapper, never()).findByEmail(any());
    }

    @Test
    void createsNaverConnectionWithOptionalUnverifiedProviderEmailOnly() {
        arrangeGeneratedUserId(61L);
        PendingSocialSignup pending = pending(
                SocialProvider.NAVER, "naver-id", "naver@example.com", null);

        long userId = service.complete(pending, acceptedForm("네이버여행자"));

        assertThat(userId).isEqualTo(61L);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insertUser(userCaptor.capture());
        assertThat(userCaptor.getValue().getUserEmail()).isNull();
        ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountMapper).insert(accountCaptor.capture());
        SocialAccount account = accountCaptor.getValue();
        assertThat(account.getProvider()).isEqualTo(SocialProvider.NAVER);
        assertThat(account.getProviderUserId()).isEqualTo("naver-id");
        assertThat(account.getProviderEmail()).isEqualTo("naver@example.com");
        assertThat(account.getProviderEmailVerified()).isNull();
        verify(userMapper, never()).findByEmail(any());
    }

    @Test
    void createsNaverConnectionWithoutProviderEmail() {
        arrangeGeneratedUserId(62L);
        PendingSocialSignup pending = pending(
                SocialProvider.NAVER, "naver-id-no-email", null, null);

        service.complete(pending, acceptedForm("네이버여행자"));

        ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountMapper).insert(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getProvider()).isEqualTo(SocialProvider.NAVER);
        assertThat(accountCaptor.getValue().getProviderEmail()).isNull();
        assertThat(accountCaptor.getValue().getProviderEmailVerified()).isNull();
    }

    /** users 에는 정규화한 값을, social_accounts 에는 provider 가 준 표기를 그대로 남긴다. */
    @Test
    void storesTheVerifiedGoogleEmailAsTheOfficialUserEmailAndKeepsTheProviderReference() {
        arrangeGeneratedUserId(41L);

        service.complete(pending("sub", " New@Example.com ", true), acceptedForm("여행자123"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insertUser(userCaptor.capture());
        assertThat(userCaptor.getValue().getUserEmail()).isEqualTo("new@example.com");
        ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountMapper).insert(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getProviderEmail()).isEqualTo("New@Example.com");
        assertThat(accountCaptor.getValue().getProviderEmailVerified()).isTrue();
        verify(userMapper).findByEmail("new@example.com");
    }

    /** Kakao/Naver 는 아직 자체 이메일 인증이 없어 기존 정책 그대로 user_email 을 비워 둔다. */
    @ParameterizedTest
    @MethodSource("providersWithoutOwnEmailVerification")
    void keepsUserEmailNullForProvidersWithoutOwnEmailVerification(PendingSocialSignup pending) {
        arrangeGeneratedUserId(41L);

        service.complete(pending, acceptedForm("여행자123"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insertUser(userCaptor.capture());
        assertThat(userCaptor.getValue().getUserEmail()).isNull();
        ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountMapper).insert(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getProviderEmail())
                .isEqualTo(pending.providerEmail());
        assertThat(accountCaptor.getValue().getProviderEmailVerified())
                .isEqualTo(pending.providerEmailVerified());
        verify(userMapper, never()).findByEmail(any());
    }

    static Stream<PendingSocialSignup> providersWithoutOwnEmailVerification() {
        return Stream.of(
                pending(SocialProvider.KAKAO, "kakao-verified", "kakao@example.com", true),
                pending(SocialProvider.KAKAO, "kakao-none", null, null),
                pending(SocialProvider.NAVER, "naver-unverified", "naver@example.com", null),
                pending(SocialProvider.NAVER, "naver-none", null, null));
    }

    /** Google 인데 인증된 이메일이 없으면 이메일 없는 계정을 만들지 않고 흐름을 끝낸다. */
    @ParameterizedTest
    @MethodSource("googleStatesWithoutVerifiedEmail")
    void refusesGoogleSignupWithoutAVerifiedProviderEmail(PendingSocialSignup pending) {
        assertThatThrownBy(() -> service.complete(pending, acceptedForm("여행자123")))
                .isInstanceOf(SocialSignupFlowException.class);

        verify(userMapper, never()).insertUser(any());
        verify(socialAccountMapper, never()).insert(any());
    }

    static Stream<PendingSocialSignup> googleStatesWithoutVerifiedEmail() {
        return Stream.of(
                pending("sub-false", "existing@example.com", false),
                pending("sub-unknown", "existing@example.com", null),
                pending("sub-missing", null, true),
                pending("sub-invalid", "not-an-email", true));
    }

    /** 같은 이메일의 users 가 이미 있으면 두 번째 계정을 만들지 않는다. */
    @Test
    void refusesToCreateASecondUserWhenTheGoogleEmailIsAlreadyTaken() {
        when(userMapper.findByEmail("existing@example.com")).thenReturn(new User());

        assertThatThrownBy(() -> service.complete(
                pending("different-google-sub", "existing@example.com", true),
                acceptedForm("새여행자")))
                .isInstanceOf(SocialSignupFlowException.class);

        verify(userMapper, never()).insertUser(any());
        verify(socialAccountMapper, never()).insert(any());
    }

    /** 사전 조회를 통과한 뒤 경합으로 UNIQUE 가 터져도 닉네임 오류로 오인하지 않는다. */
    @Test
    void emailUniqueRaceAtInsertIsReportedAsAFlowFailureNotANicknameError() {
        when(userMapper.findByEmail("new@example.com"))
                .thenReturn(null)
                .thenReturn(new User());
        doThrow(new DuplicateKeyException("user_email_UNIQUE"))
                .when(userMapper).insertUser(any());

        assertThatThrownBy(() -> service.complete(
                pending("sub", "new@example.com", true), acceptedForm("여행자123")))
                .isInstanceOf(SocialSignupFlowException.class)
                .hasMessageNotContaining("user_email_UNIQUE");

        verify(socialAccountMapper, never()).insert(any());
    }

    @Test
    void rejectsMissingOrInvalidNicknameAndRequiredConsents() {
        assertValidation("nickname", pending("sub", null, null), acceptedForm(" "));
        assertValidation("nickname", pending("sub", null, null), acceptedForm("닉네임 공백"));

        SocialSignupForm noTerms = acceptedForm("여행자123");
        noTerms.setTermsAccepted(false);
        assertValidation("termsAccepted", pending("sub", null, null), noTerms);

        SocialSignupForm noPrivacy = acceptedForm("여행자123");
        noPrivacy.setPrivacyAccepted(false);
        assertValidation("privacyAccepted", pending("sub", null, null), noPrivacy);
    }

    @Test
    void rejectsNicknameAlreadyFoundBeforeInsert() {
        when(userMapper.countByNickname("여행자123")).thenReturn(1);

        assertValidation("nickname", pending("sub", null, null), acceptedForm("여행자123"));

        verify(userMapper, never()).insertUser(any());
        verify(socialAccountMapper, never()).insert(any());
    }

    @Test
    void rejectsExpiredInvalidOrAlreadyConnectedFlowBeforeCreatingUser() {
        PendingSocialSignup expired = new PendingSocialSignup(
                "flow", SocialProvider.GOOGLE, "sub", null, null,
                Instant.now().minusSeconds(700), Instant.now().minusSeconds(100));
        assertThatThrownBy(() -> service.complete(expired, acceptedForm("여행자123")))
                .isInstanceOf(SocialSignupFlowException.class);

        SocialAccount connected = new SocialAccount();
        connected.setUserId(7L);
        when(socialAccountMapper.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "connected-sub")).thenReturn(connected);
        assertThatThrownBy(() -> service.complete(
                pending("connected-sub", null, null), acceptedForm("여행자123")))
                .isInstanceOf(SocialSignupFlowException.class);

        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void nicknameUniqueRacePropagatesRuntimeFailureBeforeSocialInsert() {
        doThrow(new DuplicateKeyException("duplicate user"))
                .when(userMapper).insertUser(any());

        assertThatThrownBy(() -> service.complete(
                pending("sub", "new@example.com", true), acceptedForm("여행자123")))
                .isInstanceOf(SocialSignupValidationException.class)
                .hasMessageNotContaining("duplicate user");

        verify(socialAccountMapper, never()).insert(any());
    }

    @Test
    void providerUniqueRaceThrowsAfterUserInsertSoTransactionRollsBackBothWrites() {
        doAnswer(invocation -> {
            invocation.<User>getArgument(0).setId(41L);
            return null;
        }).when(userMapper).insertUser(any());
        doThrow(new DuplicateKeyException("provider unique"))
                .when(socialAccountMapper).insert(any());

        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        SocialSignupService transactionalService = transactionalProxy(transactionManager);

        assertThatThrownBy(() -> transactionalService.complete(
                pending("sub", "new@example.com", true), acceptedForm("여행자123")))
                .isInstanceOf(SocialSignupFlowException.class)
                .hasMessageNotContaining("provider unique");

        verify(userMapper).insertUser(any());
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    private void arrangeGeneratedUserId(long userId) {
        doAnswer(invocation -> {
            invocation.<User>getArgument(0).setId(userId);
            return null;
        }).when(userMapper).insertUser(any());
        when(socialAccountMapper.insert(any())).thenReturn(1);
    }

    private void assertValidation(String field, PendingSocialSignup pending,
                                  SocialSignupForm form) {
        assertThatThrownBy(() -> service.complete(pending, form))
                .isInstanceOfSatisfying(SocialSignupValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo(field));
    }

    private SocialSignupForm acceptedForm(String nickname) {
        SocialSignupForm form = new SocialSignupForm();
        form.setNickname(nickname);
        form.setTermsAccepted(true);
        form.setPrivacyAccepted(true);
        return form;
    }

    private static PendingSocialSignup pending(String sub, String email, Boolean verified) {
        return pending(SocialProvider.GOOGLE, sub, email, verified);
    }

    private static PendingSocialSignup pending(SocialProvider provider,
                                                String sub,
                                                String email,
                                                Boolean verified) {
        Instant now = Instant.now();
        return new PendingSocialSignup(
                "flow-123", provider, sub, email, verified,
                now.minusSeconds(10), now.plusSeconds(590));
    }

    private SocialSignupService transactionalProxy(
            RecordingTransactionManager transactionManager) {
        TransactionInterceptor interceptor = new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(interceptor);
        return (SocialSignupService) proxyFactory.getProxy();
    }

    private static final class RecordingTransactionManager
            extends AbstractPlatformTransactionManager {

        private boolean committed;
        private boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }
    }
}
