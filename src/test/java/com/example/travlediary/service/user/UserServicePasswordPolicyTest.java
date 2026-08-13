package com.example.travlediary.service.user;

import com.example.travlediary.dto.RegistrationForm;
import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailService;
import com.example.travlediary.service.email.EmailVerificationService;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServicePasswordPolicyTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private FileUploadService fileUploadService;
    @Mock private EmailService emailService;
    @Mock private EmailVerificationService emailVerificationService;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userMapper, passwordEncoder, fileUploadService,
                emailService, emailVerificationService);
    }

    @Test
    void registrationUsesTheSharedPasswordPolicyBeforeEncoding() {
        RegistrationForm form = new RegistrationForm();
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
        when(userMapper.findByResetToken("valid-token")).thenReturn(user);

        assertThatThrownBy(() -> service.resetPassword("valid-token", "weak-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PasswordPolicy.INVALID_MESSAGE);

        verify(passwordEncoder, never()).encode(any());
        verify(userMapper, never()).updateUserPassword(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }
}
