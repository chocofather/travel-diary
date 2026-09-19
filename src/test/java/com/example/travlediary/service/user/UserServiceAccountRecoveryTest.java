package com.example.travlediary.service.user;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.InMemoryAccountAbuseGuard;
import com.example.travlediary.service.email.EmailDispatchService;
import com.example.travlediary.service.email.EmailVerificationService;
import com.example.travlediary.service.policy.PolicyConsentRecorder;
import com.example.travlediary.service.policy.SignupPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceAccountRecoveryTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailDispatchService emailDispatchService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private SignupPolicyService signupPolicyService;
    @Mock private PolicyConsentRecorder policyConsentRecorder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, passwordEncoder, emailDispatchService,
                emailVerificationService, signupPolicyService,
                new RegistrationTransactionService(userMapper, policyConsentRecorder),
                new InMemoryAccountAbuseGuard());
        ReflectionTestUtils.setField(userService, "serverUrl", "https://tripbora.example");
    }

    @Test
    void passwordRecoveryNormalizesEmailAndCreatesAThirtyMinuteToken() {
        User user = new User();
        user.setId(7L);
        when(userMapper.findActiveLocalAccountByEmailForPasswordReset("member@gmail.com"))
                .thenReturn(user);
        LocalDateTime beforeRequest = LocalDateTime.now();

        userService.processResetPasswordRequest("  MEMBER@GMAIL.COM  ");

        verify(userMapper).findActiveLocalAccountByEmailForPasswordReset("member@gmail.com");
        ArgumentCaptor<String> tokenHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> expiry = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> resetUrl = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updateResetToken(eq(7L), tokenHash.capture(), expiry.capture());
        assertThat(tokenHash.getValue()).matches("[0-9a-f]{64}");
        assertThat(expiry.getValue())
                .isAfterOrEqualTo(beforeRequest.plusMinutes(30))
                .isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(30));
        verify(emailDispatchService).dispatchPasswordResetEmail(
                eq("member@gmail.com"), resetUrl.capture(), eq(SupportedLanguage.KOREAN));
        String rawToken = resetUrl.getValue().substring(resetUrl.getValue().indexOf("token=") + 6);
        assertThat(ResetTokenHasher.hash(rawToken)).isEqualTo(tokenHash.getValue());
    }

    @Test
    void missingOrSocialOnlyAccountDoesNotCreateALocalPasswordResetToken() {
        when(userMapper.findActiveLocalAccountByEmailForPasswordReset("social@gmail.com"))
                .thenReturn(null);

        userService.processResetPasswordRequest("social@gmail.com");

        verify(userMapper, never()).updateResetToken(any(), anyString(), any(LocalDateTime.class));
        verify(emailDispatchService, never()).dispatchPasswordResetEmail(
                anyString(), anyString(), any(SupportedLanguage.class));
    }

    @Test
    void repeatedPasswordResetUsesTheSameNormalizedEmailCooldownKey() {
        User user = new User();
        user.setId(7L);
        when(userMapper.findActiveLocalAccountByEmailForPasswordReset("member@gmail.com"))
                .thenReturn(user);

        userService.processResetPasswordRequest("member@gmail.com");
        userService.processResetPasswordRequest("  MEMBER@GMAIL.COM  ");

        verify(userMapper, times(2))
                .findActiveLocalAccountByEmailForPasswordReset("member@gmail.com");
        verify(userMapper, times(1)).updateResetToken(eq(7L), anyString(), any(LocalDateTime.class));
        verify(emailDispatchService, times(1)).dispatchPasswordResetEmail(
                eq("member@gmail.com"), anyString(), eq(SupportedLanguage.KOREAN));
    }

    @Test
    void missingAccountConsumesTheSameCooldownAsAnExistingAccount() {
        when(userMapper.findActiveLocalAccountByEmailForPasswordReset("member@gmail.com"))
                .thenReturn(null, activeMember());

        userService.processResetPasswordRequest("member@gmail.com");
        userService.processResetPasswordRequest("member@gmail.com");

        verify(emailDispatchService, never()).dispatchPasswordResetEmail(
                anyString(), anyString(), any(SupportedLanguage.class));
    }

    private User activeMember() {
        User user = new User();
        user.setId(7L);
        return user;
    }
}
