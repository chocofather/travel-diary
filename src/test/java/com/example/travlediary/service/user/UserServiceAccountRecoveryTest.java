package com.example.travlediary.service.user;

import com.example.travlediary.security.InMemoryAccountAbuseGuard;
import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
                // 테스트마다 새 guard 라 첫 요청은 늘 지나간다. (cooldown 은 이 클래스의 관심사가 아니다)
                new InMemoryAccountAbuseGuard());
        ReflectionTestUtils.setField(
                userService, "serverUrl", "https://travel-diary.example");
    }

    @Test
    void usernameRecoveryNormalizesEmailAndDispatchesOnlyForAnActiveAccount() {
        User user = new User();
        user.setUsername("travel-member");
        when(userMapper.findActiveByEmailForUsernameRecovery("member@gmail.com"))
                .thenReturn(user);

        userService.processFindUsername("  MEMBER@GMAIL.COM  ");

        verify(userMapper).findActiveByEmailForUsernameRecovery("member@gmail.com");
        verify(emailDispatchService).dispatchUsernameRecoveryEmail(
                "member@gmail.com",
                "travel-member",
                "https://travel-diary.example/login",
                "https://travel-diary.example/users/find-password",
                SupportedLanguage.KOREAN);
    }

    @Test
    void usernameRecoveryDoesNotDispatchWhenNoActiveAccountExists() {
        when(userMapper.findActiveByEmailForUsernameRecovery("missing@gmail.com"))
                .thenReturn(null);

        userService.processFindUsername("missing@gmail.com");

        verify(emailDispatchService, never()).dispatchUsernameRecoveryEmail(
                anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passwordRecoveryCreatesAThirtyMinuteTokenAndDispatchesForAMatchingAccount() {
        User user = new User();
        user.setId(7L);
        when(userMapper.findByUsernameAndEmail("member", "member@gmail.com"))
                .thenReturn(user);
        LocalDateTime beforeRequest = LocalDateTime.now();

        userService.processResetPasswordRequest("  member  ", "  MEMBER@GMAIL.COM  ");

        ArgumentCaptor<String> tokenHashCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> expiryCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> resetUrlCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updateResetToken(
                eq(7L), tokenHashCaptor.capture(), expiryCaptor.capture());
        String tokenHash = tokenHashCaptor.getValue();
        assertThat(tokenHash).matches("[0-9a-f]{64}");
        assertThat(expiryCaptor.getValue())
                .isAfterOrEqualTo(beforeRequest.plusMinutes(30))
                .isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(30));
        verify(emailDispatchService).dispatchPasswordResetEmail(
                eq("member@gmail.com"), resetUrlCaptor.capture(), org.mockito.ArgumentMatchers.any());
        String resetUrl = resetUrlCaptor.getValue();
        String rawToken = resetUrl.substring(resetUrl.indexOf("token=") + "token=".length());
        assertThat(resetUrl)
                .startsWith("https://travel-diary.example/users/reset-password?token=")
                .doesNotContain(tokenHash);
        assertThat(rawToken).isNotEqualTo(tokenHash);
        assertThat(ResetTokenHasher.hash(rawToken)).isEqualTo(tokenHash);
    }

    /**
     * 같은 주소로 거듭 요청해도 60초 안에는 한 통만 나간다.
     * 화면에서 버튼을 여러 번 눌러도 메일함이 쌓이지 않는다.
     */
    @Test
    void repeatingUsernameRecoveryForOneAddressOnlySendsOneMail() {
        User user = new User();
        user.setUsername("travel-member");
        when(userMapper.findActiveByEmailForUsernameRecovery("member@gmail.com"))
                .thenReturn(user);

        userService.processFindUsername("member@gmail.com");
        userService.processFindUsername("member@gmail.com");
        // 대소문자만 다른 주소도 같은 주소로 본다
        userService.processFindUsername("MEMBER@GMAIL.COM");

        verify(emailDispatchService, org.mockito.Mockito.times(1))
                .dispatchUsernameRecoveryEmail(eq("member@gmail.com"), anyString(),
                        anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * 재설정 요청을 거듭해도 토큰을 새로 발급하지 않는다.
     *
     * <p>재설정 토큰은 한 회원에 하나뿐이라 새로 발급하면 방금 메일로 받은 링크가 곧바로
     * 무효가 된다. cooldown 안에서는 발급 자체를 건너뛰어 먼저 받은 링크를 그대로 쓸 수 있다.
     */
    @Test
    void repeatingAPasswordResetRequestDoesNotInvalidateTheLinkAlreadySent() {
        User user = new User();
        user.setId(7L);
        when(userMapper.findByUsernameAndEmail("member", "member@gmail.com"))
                .thenReturn(user);

        userService.processResetPasswordRequest("member", "member@gmail.com");
        userService.processResetPasswordRequest("member", "member@gmail.com");

        verify(userMapper, org.mockito.Mockito.times(1)).updateResetToken(
                eq(7L), anyString(), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(emailDispatchService, org.mockito.Mockito.times(1))
                .dispatchPasswordResetEmail(eq("member@gmail.com"), anyString(),
                        org.mockito.ArgumentMatchers.any());
    }

    /**
     * 없는 주소도 cooldown 을 똑같이 쓴다.
     *
     * <p>있는 계정만 세면 "셌는지" 로 존재 여부가 갈린다. 여기서는 없는 주소로 한 번 부른 뒤에도
     * 곧바로 메일이 나가지 않는 것으로 그 차이가 없음을 본다.
     */
    @Test
    void anAddressWithoutAnAccountConsumesTheSameCooldown() {
        when(userMapper.findActiveByEmailForUsernameRecovery("member@gmail.com"))
                .thenReturn(null, activeMember());

        userService.processFindUsername("member@gmail.com");
        userService.processFindUsername("member@gmail.com");

        verify(emailDispatchService, never()).dispatchUsernameRecoveryEmail(
                anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    private User activeMember() {
        User user = new User();
        user.setUsername("travel-member");
        return user;
    }

    @Test
    void passwordRecoveryDoesNotCreateATokenOrDispatchForAMissingAccount() {
        when(userMapper.findByUsernameAndEmail("missing", "missing@gmail.com"))
                .thenReturn(null);

        userService.processResetPasswordRequest("missing", "missing@gmail.com");

        verify(userMapper, never()).updateResetToken(
                org.mockito.ArgumentMatchers.anyLong(), anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(emailDispatchService, never()).dispatchPasswordResetEmail(
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * 메일 발송은 @Async 라 워커 스레드에서 locale 을 다시 읽을 수 없다.
     * 요청 스레드의 언어가 인자로 그대로 전달되어야 한다.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "ko, KOREAN", "en, ENGLISH", "ja, JAPANESE",
            "zh-CN, CHINESE_SIMPLIFIED", "zh-TW, CHINESE_TRADITIONAL", "fr, KOREAN"
    })
    void recoveryMailsCarryTheRequestLanguage(
            String languageTag, com.example.travlediary.config.i18n.SupportedLanguage expected) {
        com.example.travlediary.model.User member = new com.example.travlediary.model.User();
        member.setId(7L);
        member.setUsername("travel-member");
        member.setUserEmail("member@gmail.com");
        when(userMapper.findByUsernameAndEmail("travel-member", "member@gmail.com"))
                .thenReturn(member);
        when(userMapper.findActiveByEmailForUsernameRecovery("member@gmail.com"))
                .thenReturn(member);

        org.springframework.context.i18n.LocaleContextHolder.setLocale(
                java.util.Locale.forLanguageTag(languageTag));
        try {
            userService.processResetPasswordRequest("travel-member", "member@gmail.com");
            userService.processFindUsername("member@gmail.com");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }

        verify(emailDispatchService).dispatchPasswordResetEmail(
                eq("member@gmail.com"), anyString(), eq(expected));
        verify(emailDispatchService).dispatchUsernameRecoveryEmail(
                eq("member@gmail.com"), anyString(), anyString(), anyString(), eq(expected));
    }
}
