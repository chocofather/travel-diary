package com.example.travlediary.service.user;

import com.example.travlediary.security.InMemoryAccountAbuseGuard;
import com.example.travlediary.dto.RegistrationForm;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceNicknameValidationTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailDispatchService emailDispatchService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private SignupPolicyService signupPolicyService;
    @Mock private PolicyConsentRecorder policyConsentRecorder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        // 정책 활성화 전 상태가 기본값이다. 닉네임 검증만 남는다.
        lenient().when(signupPolicyService.loadSignupPolicies())
                .thenReturn(SignupPolicySet.empty());
        userService = new UserService(userMapper, passwordEncoder, emailDispatchService,
                emailVerificationService, signupPolicyService,
                new RegistrationTransactionService(userMapper, policyConsentRecorder),
                // 테스트마다 새 guard 라 첫 요청은 늘 지나간다. (cooldown 은 이 클래스의 관심사가 아니다)
                new InMemoryAccountAbuseGuard());
    }

    @Test
    void registrationStoresTheStrippedValidNickname() {
        RegistrationForm form = registrationForm("  민준2026  ");
        when(passwordEncoder.encode("Password!")).thenReturn("encoded-password");

        userService.registerUser(form);

        verify(userMapper).countByNickname("민준2026");
        verify(userMapper).insertUser(any());
        verify(emailVerificationService).requestInitialVerification(any());
    }

    @Test
    void registrationRejectsInvalidNicknameBeforePersistingUser() {
        RegistrationForm form = registrationForm("여행 민준");

        assertThatThrownBy(() -> userService.registerUser(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(NicknamePolicy.INVALID_MESSAGE);

        verify(userMapper, never()).insertUser(any());
        verify(passwordEncoder, never()).encode(any());
        verify(emailVerificationService, never()).requestInitialVerification(any());
    }

    @Test
    void registrationRejectsForbiddenNicknameBeforePersistingUser() {
        RegistrationForm form = registrationForm("Admin123");

        assertThatThrownBy(() -> userService.registerUser(form))
                .isInstanceOf(NicknamePolicy.ViolationException.class)
                .hasMessage(NicknamePolicy.FORBIDDEN_MESSAGE);

        verify(userMapper, never()).insertUser(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void signupNicknameCheckRejectsForbiddenNameBeforeDuplicateQuery() {
        assertThatThrownBy(() -> userService.isNicknameExists("관12리34자"))
                .isInstanceOf(NicknamePolicy.ViolationException.class)
                .hasMessage(NicknamePolicy.FORBIDDEN_MESSAGE);

        verify(userMapper, never()).countByNickname(any());
    }

    @Test
    void signupNicknameCheckNormalizesValidNameBeforeDuplicateQuery() {
        when(userMapper.countByNickname("여행왕123")).thenReturn(0);

        userService.isNicknameExists("  여행왕123  ");

        verify(userMapper).countByNickname("여행왕123");
    }

    private RegistrationForm registrationForm(String nickname) {
        RegistrationForm form = new RegistrationForm();
        // 연령 확인은 이 테스트들의 관심사가 아니므로 통과하는 값을 기본으로 둔다.
        form.setBirthDate("2000-01-01");
        form.setNickname(nickname);
        form.setUsername("member");
        form.setUserPassword("Password!");
        form.setPasswordConfirm("Password!");
        form.setUserEmail("member@example.com");
        return form;
    }
}
