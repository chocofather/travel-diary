package com.example.travlediary.service.user;

import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.InMemoryAccountAbuseGuard;
import com.example.travlediary.service.email.EmailDispatchService;
import com.example.travlediary.service.email.EmailVerificationService;
import com.example.travlediary.service.policy.SignupPolicyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 비밀번호 재설정이 한 트랜잭션으로 묶이는지.
 *
 * <p>비밀번호 변경과 토큰 폐기 중 한쪽만 커밋되면, 비밀번호는 바뀌었는데 재설정 토큰이
 * 만료 전까지 살아 있어 메일을 가로챈 쪽이 다시 바꿀 수 있는 창이 남는다.
 *
 * <p>어노테이션이 붙어 있는지가 아니라 <b>프록시가 실제로 커밋·롤백을 요청하는지</b> 를 본다.
 * {@code resetPassword} 는 2-인자 판이 3-인자 판을 같은 객체에서 부르는 구조라, 진입점마다
 * 경계가 걸려 있지 않으면 한쪽 경로에서 트랜잭션이 사라진다.
 */
class UserServiceResetPasswordTransactionTest {

    private static final String RAW_TOKEN = "11111111-2222-4333-8444-555555555555";

    private AnnotationConfigApplicationContext context;
    private UserService userService;
    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        context = new AnnotationConfigApplicationContext();
        context.registerBean(PlatformTransactionManager.class, () -> transactionManager);
        context.registerBean(UserService.class, () -> new UserService(
                userMapper, passwordEncoder,
                mock(EmailDispatchService.class), mock(EmailVerificationService.class),
                mock(SignupPolicyService.class), mock(RegistrationTransactionService.class),
                new InMemoryAccountAbuseGuard()));
        context.register(TransactionConfig.class);
        context.refresh();

        userService = context.getBean(UserService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    /** 경계가 실제로 걸려 있다. (프록시가 아니면 이 단언이 먼저 깨진다) */
    @Test
    void theServiceIsActuallyProxiedForTransactions() {
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(userService)).isTrue();
    }

    /** 정상 재설정: 비밀번호가 바뀌고 토큰이 폐기되며 커밋된다. */
    @Test
    void aSuccessfulResetChangesThePasswordAndClearsTheTokenInOneCommit() {
        givenValidToken();
        when(passwordEncoder.encode("NewPass!1")).thenReturn("{bcrypt}new");

        userService.resetPassword(RAW_TOKEN, "NewPass!1", "NewPass!1");

        var order = inOrder(userMapper, transactionManager);
        order.verify(userMapper).updateUserPassword(7L, "{bcrypt}new");
        order.verify(userMapper).clearResetToken(7L);
        order.verify(transactionManager).commit(any(TransactionStatus.class));
        verify(transactionManager, never()).rollback(any(TransactionStatus.class));
    }

    /** 토큰 폐기가 실패하면 비밀번호 변경도 되돌린다. */
    @Test
    void aFailureClearingTheTokenRollsBackThePasswordChange() {
        givenValidToken();
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}new");
        doThrow(new IllegalStateException("clear failed")).when(userMapper).clearResetToken(7L);

        assertThatThrownBy(() -> userService.resetPassword(RAW_TOKEN, "NewPass!1", "NewPass!1"))
                .isInstanceOf(IllegalStateException.class);

        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(transactionManager, never()).commit(any(TransactionStatus.class));
    }

    /** 비밀번호 변경이 실패하면 토큰은 건드리지 않고 되돌린다. */
    @Test
    void aFailureChangingThePasswordLeavesTheTokenUntouched() {
        givenValidToken();
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}new");
        doThrow(new IllegalStateException("update failed"))
                .when(userMapper).updateUserPassword(anyLong(), anyString());

        assertThatThrownBy(() -> userService.resetPassword(RAW_TOKEN, "NewPass!1", "NewPass!1"))
                .isInstanceOf(IllegalStateException.class);

        verify(userMapper, never()).clearResetToken(anyLong());
        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(transactionManager, never()).commit(any(TransactionStatus.class));
    }

    /** 확인 입력 없는 2-인자 진입점도 같은 경계를 갖는다. (프록시를 지나지 않으면 깨진다) */
    @Test
    void theTwoArgumentEntryPointAlsoRunsInsideATransaction() {
        givenValidToken();
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}new");

        userService.resetPassword(RAW_TOKEN, "NewPass!1");

        verify(userMapper).updateUserPassword(7L, "{bcrypt}new");
        verify(userMapper).clearResetToken(7L);
        verify(transactionManager).commit(any(TransactionStatus.class));
    }

    /* ===== 기존 토큰 정책 회귀 ===== */

    /** 없는 토큰은 아무것도 바꾸지 않는다. */
    @Test
    void anUnknownTokenChangesNothing() {
        when(userMapper.findByResetToken(anyString())).thenReturn(null);

        assertThatThrownBy(() -> userService.resetPassword(RAW_TOKEN, "NewPass!1", "NewPass!1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(UserService.INVALID_RESET_TOKEN_MESSAGE);

        verify(userMapper, never()).updateUserPassword(anyLong(), anyString());
        verify(userMapper, never()).clearResetToken(anyLong());
    }

    /** 만료된 토큰도 거절한다. */
    @Test
    void anExpiredTokenIsRejected() {
        User user = member();
        user.setResetTokenExp(LocalDateTime.now().minusMinutes(1));
        when(userMapper.findByResetToken(anyString())).thenReturn(user);

        assertThatThrownBy(() -> userService.resetPassword(RAW_TOKEN, "NewPass!1", "NewPass!1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(UserService.INVALID_RESET_TOKEN_MESSAGE);

        verify(userMapper, never()).updateUserPassword(anyLong(), anyString());
    }

    /** 확인 입력이 다르면 바꾸지 않는다. */
    @Test
    void aMismatchedConfirmationIsRejected() {
        givenValidToken();

        assertThatThrownBy(() -> userService.resetPassword(RAW_TOKEN, "NewPass!1", "Other!1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PasswordPolicy.MISMATCH_MESSAGE);

        verify(userMapper, never()).updateUserPassword(anyLong(), anyString());
    }

    /** 지금 쓰는 비밀번호와 같으면 바꾸지 않는다. */
    @Test
    void reusingTheCurrentPasswordIsRejected() {
        givenValidToken();
        when(passwordEncoder.matches(eq("NewPass!1"), anyString())).thenReturn(true);

        assertThatThrownBy(() -> userService.resetPassword(RAW_TOKEN, "NewPass!1", "NewPass!1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(UserService.SAME_AS_CURRENT_PASSWORD_MESSAGE);

        verify(userMapper, never()).updateUserPassword(anyLong(), anyString());
        verify(userMapper, never()).clearResetToken(anyLong());
    }

    /** 정책에 맞지 않는 비밀번호는 바꾸지 않는다. (72자 상한 포함) */
    @Test
    void aPasswordOutsideThePolicyIsRejected() {
        givenValidToken();
        String tooLong = "!" + "a".repeat(PasswordPolicy.MAX_LENGTH);

        assertThatThrownBy(() -> userService.resetPassword(RAW_TOKEN, tooLong, tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PasswordPolicy.INVALID_MESSAGE);

        verify(userMapper, never()).updateUserPassword(anyLong(), anyString());
    }

    /* ===== 도우미 ===== */

    @EnableTransactionManagement
    static class TransactionConfig {
    }

    private void givenValidToken() {
        when(userMapper.findByResetToken(ResetTokenHasher.hash(RAW_TOKEN))).thenReturn(member());
    }

    private User member() {
        User user = new User();
        user.setId(7L);
        user.setUserPassword("{bcrypt}current");
        user.setResetTokenExp(LocalDateTime.now().plusMinutes(10));
        return user;
    }
}
