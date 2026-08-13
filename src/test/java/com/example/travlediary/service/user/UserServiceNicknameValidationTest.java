package com.example.travlediary.service.user;

import com.example.travlediary.dto.RegistrationForm;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailDispatchService;
import com.example.travlediary.service.email.EmailVerificationService;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceNicknameValidationTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private FileUploadService fileUploadService;
    @Mock private EmailDispatchService emailDispatchService;
    @Mock private EmailVerificationService emailVerificationService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, passwordEncoder, fileUploadService,
                emailDispatchService, emailVerificationService);
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
        form.setNickname(nickname);
        form.setUsername("member");
        form.setUserPassword("Password!");
        form.setPasswordConfirm("Password!");
        form.setUserEmail("member@example.com");
        form.setFullName("여행자");
        return form;
    }
}
