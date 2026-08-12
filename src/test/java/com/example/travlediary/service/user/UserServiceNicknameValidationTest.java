package com.example.travlediary.service.user;

import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailService;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceNicknameValidationTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private FileUploadService fileUploadService;
    @Mock
    private EmailService emailService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userMapper, passwordEncoder, fileUploadService, emailService);
    }

    @Test
    void registrationStoresTheStrippedValidNickname() {
        User user = registrationUser("  민준2026  ");
        when(passwordEncoder.encode("Password!")).thenReturn("encoded-password");

        userService.registerUser(user, null);

        assertThat(user.getNickname()).isEqualTo("민준2026");
        verify(userMapper).insertUser(user);
        verify(emailService).sendVerificationEmail(
                org.mockito.ArgumentMatchers.eq("member@example.com"),
                org.mockito.ArgumentMatchers.eq("여행일기 이메일 인증"),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void registrationRejectsInvalidNicknameBeforePersistingUser() {
        User user = registrationUser("여행 민준");

        assertThatThrownBy(() -> userService.registerUser(user, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(NicknamePolicy.INVALID_MESSAGE);

        verify(userMapper, never()).insertUser(org.mockito.ArgumentMatchers.any());
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(emailService, never()).sendVerificationEmail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void registrationRejectsForbiddenNicknameBeforePersistingUser() {
        User user = registrationUser("Admin123");

        assertThatThrownBy(() -> userService.registerUser(user, null))
                .isInstanceOf(NicknamePolicy.ViolationException.class)
                .hasMessage(NicknamePolicy.FORBIDDEN_MESSAGE);

        verify(userMapper, never()).insertUser(org.mockito.ArgumentMatchers.any());
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void registrationRejectsForbiddenNicknameDisguisedWithDigits() {
        User user = registrationUser("병12신");

        assertThatThrownBy(() -> userService.registerUser(user, null))
                .isInstanceOf(NicknamePolicy.ViolationException.class)
                .hasMessage(NicknamePolicy.FORBIDDEN_MESSAGE);

        verify(userMapper, never()).insertUser(org.mockito.ArgumentMatchers.any());
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void signupNicknameCheckRejectsForbiddenNameBeforeDuplicateQuery() {
        assertThatThrownBy(() -> userService.isNicknameExists("관12리34자"))
                .isInstanceOf(NicknamePolicy.ViolationException.class)
                .hasMessage(NicknamePolicy.FORBIDDEN_MESSAGE);

        verify(userMapper, never()).countByNickname(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void signupNicknameCheckNormalizesValidNameBeforeDuplicateQuery() {
        when(userMapper.countByNickname("여행왕123")).thenReturn(0);

        assertThat(userService.isNicknameExists("  여행왕123  ")).isFalse();

        verify(userMapper).countByNickname("여행왕123");
    }

    private User registrationUser(String nickname) {
        User user = new User();
        user.setNickname(nickname);
        user.setUsername("member");
        user.setUserPassword("Password!");
        user.setUserEmail("member@example.com");
        return user;
    }
}
