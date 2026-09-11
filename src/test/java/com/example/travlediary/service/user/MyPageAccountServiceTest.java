package com.example.travlediary.service.user;

import com.example.travlediary.dto.AccountEditForm;
import com.example.travlediary.dto.PasswordChangeForm;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.comment.CommentLikeMapper;
import com.example.travlediary.repository.course.CourseCommentMapper;
import com.example.travlediary.repository.post.PostCommentMapper;
import com.example.travlediary.repository.user.SocialAccountMapper;
import com.example.travlediary.repository.user.UserMapper;
import org.springframework.aop.framework.ProxyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyPageAccountServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private BookmarkMapper bookmarkMapper;
    @Mock private CommentLikeMapper commentLikeMapper;
    @Mock private PostCommentMapper postCommentMapper;
    @Mock private CourseCommentMapper courseCommentMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SocialAccountMapper socialAccountMapper;

    private MyPageAccountService service;

    @BeforeEach
    void setUp() {
        // 익명화/흔적 정리는 관리자 강제탈퇴와 공용하는 실제 컴포넌트를 그대로 사용한다.
        // 유예 기한을 그대로 확인하려고 시계를 고정한다.
        service = new MyPageAccountService(
                userMapper,
                new AccountAnonymizationService(bookmarkMapper, commentLikeMapper,
                        postCommentMapper, courseCommentMapper),
                passwordEncoder,
                socialAccountMapper,
                messages(),
                FIXED_CLOCK);
    }

    /** 탈퇴 확인 문구는 화면과 같은 messages 번들에서 온다. */
    private static org.springframework.context.MessageSource messages() {
        var messageSource = new org.springframework.context.support.ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    private static final java.time.Clock FIXED_CLOCK = java.time.Clock.fixed(
            java.time.Instant.parse("2026-09-11T03:00:00Z"), java.time.ZoneOffset.UTC);
    private static final java.time.LocalDateTime NOW =
            java.time.LocalDateTime.now(FIXED_CLOCK);

    @Test
    void checksLocalPasswordCapabilityByUserIdWithoutLoadingThePasswordHash() {
        when(userMapper.hasLocalPasswordById(7L)).thenReturn(true);

        assertThat(service.hasLocalPassword(7L)).isTrue();

        verify(userMapper).hasLocalPasswordById(7L);
        verify(userMapper, never()).findActiveAccountSecurityById(7L);
    }

    @Test
    void verifiesCurrentPasswordWithTheStoredHash() {
        User account = activeAccount(UserRole.USER);
        when(userMapper.findActiveAccountSecurityById(7L)).thenReturn(account);
        when(passwordEncoder.matches("Password!", "encoded-password")).thenReturn(true);

        assertThat(service.verifyCurrentPassword(7L, "Password!")).isTrue();

        verify(passwordEncoder).matches("Password!", "encoded-password");
    }

    @Test
    void updatesOnlyNormalizedEditableDetails() {
        AccountEditForm form = new AccountEditForm();
        form.setFullName("  여행 민준  ");
        form.setUserPhone("01012345678");
        when(userMapper.updateAccountDetails(7L, "여행 민준", "010-1234-5678"))
                .thenReturn(1);

        service.updateAccountDetails(7L, form);

        // 생년월일은 이 경로에서 갱신하지 않는다
        verify(userMapper).updateAccountDetails(7L, "여행 민준", "010-1234-5678");
        assertThat(form.getFullName()).isEqualTo("여행 민준");
        assertThat(form.getUserPhone()).isEqualTo("010-1234-5678");
    }

    @Test
    void rejectsInvalidDetailsBeforeUpdate() {
        AccountEditForm form = new AccountEditForm();
        form.setFullName(" ");

        assertThatThrownBy(() -> service.updateAccountDetails(7L, form))
                .isInstanceOf(AccountValidationException.class)
                .hasMessage("이름을 입력해주세요.");

        verify(userMapper, never()).updateAccountDetails(
                org.mockito.ArgumentMatchers.anyLong(), anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void changesPasswordWithTheSharedPolicyAndBcryptEncoder() {
        PasswordChangeForm form = new PasswordChangeForm();
        form.setNewPassword("NewPassword!");
        form.setNewPasswordConfirm("NewPassword!");
        when(passwordEncoder.encode("NewPassword!")).thenReturn("new-encoded-password");
        when(userMapper.updateActiveUserPassword(7L, "new-encoded-password")).thenReturn(1);

        service.changePassword(7L, form);

        verify(passwordEncoder).encode("NewPassword!");
        verify(userMapper).updateActiveUserPassword(7L, "new-encoded-password");
    }

    /**
     * 본인 확인은 계정 및 보안 진입 단계의 재인증이 이미 끝냈다.
     * 여기서는 비밀번호를 다시 받지 않고 확인 문구만 서버에서 다시 본다.
     */
    @Test
    void aWrongConfirmationPhraseMakesNoMutation() {
        User account = activeAccount(UserRole.USER);
        when(userMapper.findActiveAccountSecurityByIdForUpdate(7L)).thenReturn(account);

        assertThatThrownBy(() -> service.withdraw(7L, "탈퇴할래요"))
                .isInstanceOf(AccountValidationException.class)
                .hasMessage("확인 문구가 일치하지 않습니다.");

        verify(userMapper, never()).requestWithdrawal(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(passwordEncoder, bookmarkMapper, commentLikeMapper,
                postCommentMapper, courseCommentMapper);
        verify(userMapper, never()).deactivateAccount(
                org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "   ", "탈퇴", "I request deletion"})
    void anEmptyOrPartialConfirmationPhraseIsRejected(String phrase) {
        when(userMapper.findActiveAccountSecurityByIdForUpdate(7L))
                .thenReturn(activeAccount(UserRole.USER));

        assertThatThrownBy(() -> service.withdraw(7L, phrase))
                .isInstanceOf(AccountValidationException.class);

        verify(userMapper, never()).requestWithdrawal(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 입력 도중 언어를 바꿔도 지원 언어의 문구면 접수된다. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "탈퇴를 신청합니다", "  탈퇴를  신청합니다 ", "I request account deletion",
            "退会を申請します", "我申请注销账号", "我申請註銷帳號"})
    void theConfirmationPhraseOfEverySupportedLanguageIsAccepted(String phrase) {
        when(userMapper.findActiveAccountSecurityByIdForUpdate(7L))
                .thenReturn(activeAccount(UserRole.USER));
        when(userMapper.requestWithdrawal(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(UserStatus.WITHDRAWAL_PENDING),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);

        service.withdraw(7L, phrase);

        verify(userMapper).requestWithdrawal(7L, UserStatus.WITHDRAWAL_PENDING,
                NOW, NOW.plusDays(30));
    }

    @Test
    void adminWithdrawalIsRejectedBeforeAnyMutation() {
        when(userMapper.findActiveAccountSecurityByIdForUpdate(99L))
                .thenReturn(activeAccount(UserRole.ADMIN));

        assertThatThrownBy(() -> service.withdraw(99L, "탈퇴를 신청합니다"))
                .isInstanceOf(AccountValidationException.class)
                .hasMessage("관리자 계정은 마이페이지에서 탈퇴할 수 없습니다.");

        verifyNoInteractions(passwordEncoder, bookmarkMapper, commentLikeMapper,
                postCommentMapper, courseCommentMapper);
    }

    @Test
    void withdrawalRequestOnlySchedulesThePurgeAndKeepsEverythingElse() {
        User account = activeAccount(UserRole.USER);
        when(userMapper.findActiveAccountSecurityByIdForUpdate(7L)).thenReturn(account);
        when(userMapper.requestWithdrawal(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(UserStatus.WITHDRAWAL_PENDING),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);

        service.withdraw(7L, "탈퇴를 신청합니다");

        // 상태와 유예 일정만 기록한다. 30일 뒤 최종 파기 전까지는 아무것도 지우지 않는다.
        verify(userMapper).requestWithdrawal(7L, UserStatus.WITHDRAWAL_PENDING,
                NOW, NOW.plusDays(30));
        assertThat(MyPageAccountService.WITHDRAWAL_GRACE_PERIOD)
                .isEqualTo(java.time.Duration.ofDays(30));

        // 익명화도, 개인 흔적 정리도 일어나지 않는다.
        verify(userMapper, never()).deactivateAccount(
                org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(bookmarkMapper, commentLikeMapper,
                postCommentMapper, courseCommentMapper);
        verify(socialAccountMapper, never()).deleteAllByUserId(
                org.mockito.ArgumentMatchers.anyLong());
        verify(socialAccountMapper, never()).deleteAllByUserId(7L);
    }

    @Test
    void withdrawalRunsInOneWriteTransaction() throws NoSuchMethodException {
        Transactional transactional = MyPageAccountService.class
                .getMethod("withdraw", Long.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    void socialWithdrawalRequestKeepsTheSocialLinkWithoutAPasswordCheck() {
        User account = activeAccount(UserRole.USER);
        account.setUserPassword(null);
        when(userMapper.findActiveAccountSecurityByIdForUpdate(7L)).thenReturn(account);
        when(userMapper.requestWithdrawal(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(UserStatus.WITHDRAWAL_PENDING),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);

        service.withdrawAfterSocialReauthentication(7L);

        verifyNoInteractions(passwordEncoder);
        verify(userMapper).requestWithdrawal(7L, UserStatus.WITHDRAWAL_PENDING,
                NOW, NOW.plusDays(30));

        // 유예 기간에는 소셜 연결도 개인 흔적도 그대로 둔다.
        verify(socialAccountMapper, never()).deleteAllByUserId(
                org.mockito.ArgumentMatchers.anyLong());
        verify(userMapper, never()).deactivateAccount(
                org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(bookmarkMapper, commentLikeMapper,
                postCommentMapper, courseCommentMapper);
    }

    @Test
    void withdrawalRequestThatChangedNoRowIsReportedAsAConflict() {
        User account = activeAccount(UserRole.USER);
        account.setUserPassword(null);
        when(userMapper.findActiveAccountSecurityByIdForUpdate(7L)).thenReturn(account);
        // 이미 ACTIVE 가 아니면 UPDATE 가 0 행을 바꾼다(중복 신청 가드).
        when(userMapper.requestWithdrawal(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.withdrawAfterSocialReauthentication(7L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void socialWithdrawalCannotBypassAStoredLocalPassword() {
        when(userMapper.findActiveAccountSecurityByIdForUpdate(7L))
                .thenReturn(activeAccount(UserRole.USER));

        assertThatThrownBy(() -> service.withdrawAfterSocialReauthentication(7L))
                .isInstanceOf(AccountValidationException.class);

        verify(userMapper, never()).deactivateAccount(
                org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void socialWithdrawalDatabasePolicyRunsInItsOwnWriteTransaction()
            throws NoSuchMethodException {
        Transactional transactional = MyPageAccountService.class
                .getMethod("withdrawAfterSocialReauthentication", Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    void withdrawalRequestFailureRollsBackTheWithdrawalTransaction() {
        User account = activeAccount(UserRole.USER);
        account.setUserPassword(null);
        when(userMapper.findActiveAccountSecurityByIdForUpdate(7L)).thenReturn(account);
        org.mockito.Mockito.doThrow(new IllegalStateException("update failed"))
                .when(userMapper).requestWithdrawal(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        MyPageAccountService transactionalService = transactionalProxy(transactionManager);

        assertThatThrownBy(() ->
                transactionalService.withdrawAfterSocialReauthentication(7L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    private MyPageAccountService transactionalProxy(
            RecordingTransactionManager transactionManager) {
        TransactionInterceptor interceptor = new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(interceptor);
        return (MyPageAccountService) proxyFactory.getProxy();
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

    private User activeAccount(UserRole role) {
        User account = new User();
        account.setId(7L);
        account.setUsername("minjun");
        account.setUserEmail("member@example.com");
        account.setNickname("여행자");
        account.setUserPassword("encoded-password");
        account.setUserRole(role);
        account.setStatus(UserStatus.ACTIVE);
        return account;
    }
}
