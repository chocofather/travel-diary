package com.example.travlediary.service.user;

import com.example.travlediary.dto.RegistrationForm;
import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailDispatchService;
import com.example.travlediary.service.email.EmailVerificationService;
import com.example.travlediary.service.policy.PolicyConsentRecorder;
import com.example.travlediary.service.policy.SignupPolicyService;
import com.example.travlediary.service.policy.SignupPolicySet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServicePasswordPolicyTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailDispatchService emailDispatchService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private SignupPolicyService signupPolicyService;
    @Mock private PolicyConsentRecorder policyConsentRecorder;

    private UserService service;

    @BeforeEach
    void setUp() {
        // 정책 활성화 전 상태가 기본값이다. 필수 동의가 없어 기존 가입 검증만 남는다.
        lenient().when(signupPolicyService.loadSignupPolicies())
                .thenReturn(SignupPolicySet.empty());
        service = new UserService(userMapper, passwordEncoder, emailDispatchService,
                emailVerificationService, signupPolicyService,
                new RegistrationTransactionService(userMapper, policyConsentRecorder));
    }

    @Test
    void registrationUsesTheSharedPasswordPolicyBeforeEncoding() {
        RegistrationForm form = new RegistrationForm();
        // 연령 확인은 이 테스트들의 관심사가 아니므로 통과하는 값을 기본으로 둔다.
        form.setBirthDate("2000-01-01");
        form.setNickname("여행자123");
        form.setUsername("member");
        form.setUserEmail("member@example.com");
        form.setUserPassword("no-special-password");
        form.setPasswordConfirm("no-special-password");

        assertThatThrownBy(() -> service.registerUser(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PasswordPolicy.INVALID_MESSAGE);

        verify(passwordEncoder, never()).encode(any());
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void resetPasswordUsesTheSameServerPolicyBeforeEncodingOrUpdate() {
        User user = new User();
        user.setId(7L);
        user.setResetTokenExp(LocalDateTime.now().plusMinutes(10));
        when(userMapper.findByResetToken(ResetTokenHasher.hash("valid-token"))).thenReturn(user);

        assertThatThrownBy(() -> service.resetPassword("valid-token", "weak-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PasswordPolicy.INVALID_MESSAGE);

        verify(passwordEncoder, never()).encode(any());
        verify(userMapper, never()).updateUserPassword(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(userMapper, never()).clearResetToken(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void resetPasswordRejectsAMismatchedConfirmationBeforeEncodingOrUpdate() {
        User user = new User();
        user.setId(7L);
        user.setResetTokenExp(LocalDateTime.now().plusMinutes(10));
        when(userMapper.findByResetToken(ResetTokenHasher.hash("valid-token"))).thenReturn(user);

        assertThatThrownBy(() -> service.resetPassword(
                "valid-token", "StrongPassword!", "DifferentPassword!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PasswordPolicy.MISMATCH_MESSAGE);

        verify(passwordEncoder, never()).encode(any());
        verify(userMapper, never()).updateUserPassword(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(userMapper, never()).clearResetToken(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void validResetUpdatesThePasswordAndClearsTheUsedToken() {
        User user = new User();
        user.setId(7L);
        user.setUserPassword("current-password-hash");
        user.setResetTokenExp(LocalDateTime.now().plusMinutes(10));
        when(userMapper.findByResetToken(ResetTokenHasher.hash("valid-token"))).thenReturn(user);
        when(passwordEncoder.matches("StrongPassword!", "current-password-hash"))
                .thenReturn(false);
        when(passwordEncoder.encode("StrongPassword!")).thenReturn("encoded-password");

        service.resetPassword("valid-token", "StrongPassword!", "StrongPassword!");

        verify(userMapper).updateUserPassword(7L, "encoded-password");
        verify(userMapper).clearResetToken(7L);
    }

    @Test
    void resetPasswordRejectsTheCurrentPasswordWithoutConsumingTheToken() {
        User user = new User();
        user.setId(7L);
        user.setUserPassword("current-password-hash");
        user.setResetTokenExp(LocalDateTime.now().plusMinutes(10));
        when(userMapper.findByResetToken(ResetTokenHasher.hash("valid-token"))).thenReturn(user);
        when(passwordEncoder.matches("StrongPassword!", "current-password-hash"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.resetPassword(
                "valid-token", "StrongPassword!", "StrongPassword!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(UserService.SAME_AS_CURRENT_PASSWORD_MESSAGE);

        verify(passwordEncoder, never()).encode(any());
        verify(userMapper, never()).updateUserPassword(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(userMapper, never()).clearResetToken(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void expiredResetTokenCannotChangeThePassword() {
        User user = new User();
        user.setId(7L);
        user.setResetTokenExp(LocalDateTime.now().minusMinutes(1));
        when(userMapper.findByResetToken(ResetTokenHasher.hash("expired-token"))).thenReturn(user);

        assertThatThrownBy(() -> service.resetPassword(
                "expired-token", "StrongPassword!", "StrongPassword!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(UserService.INVALID_RESET_TOKEN_MESSAGE);

        verify(passwordEncoder, never()).encode(any());
        verify(userMapper, never()).updateUserPassword(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(userMapper, never()).clearResetToken(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void invalidRawTokenIsHashedBeforeLookupAndCannotChangeThePassword() {
        assertThatThrownBy(() -> service.resetPassword(
                "invalid-token", "StrongPassword!", "StrongPassword!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(UserService.INVALID_RESET_TOKEN_MESSAGE);

        verify(userMapper).findByResetToken(ResetTokenHasher.hash("invalid-token"));
        verify(userMapper, never()).findByResetToken("invalid-token");
        verify(userMapper, never()).updateUserPassword(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(userMapper, never()).clearResetToken(org.mockito.ArgumentMatchers.anyLong());
    }
}
