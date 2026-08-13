package com.example.travlediary.service.user;

import com.example.travlediary.dto.RegistrationForm;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailService;
import com.example.travlediary.service.email.EmailVerificationService;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceRegistrationTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private FileUploadService fileUploadService;
    @Mock private EmailService emailService;
    @Mock private EmailVerificationService emailVerificationService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, passwordEncoder, fileUploadService,
                emailService, emailVerificationService);
    }

    @Test
    void registrationNormalizesEmailAndStoresOnlyServerControlledAccountState() {
        RegistrationForm form = validForm();
        form.setUserEmail("  MEMBER@GMAIL.COM  ");
        form.setFullName("  Hong   Gil Dong  ");
        when(passwordEncoder.encode("Password!")).thenReturn("encoded");
        when(emailVerificationService.requestInitialVerification(any())).thenReturn(true);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setVerificationToken("generated-token");
            user.setVerificationTokenExp(LocalDateTime.of(2026, 8, 14, 10, 0));
            user.setVerificationRequestedAt(LocalDateTime.of(2026, 8, 13, 10, 0));
            return null;
        }).when(emailVerificationService).initializeVerification(any());

        RegistrationResult result = userService.registerUser(form);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insertUser(userCaptor.capture());
        User stored = userCaptor.getValue();
        assertThat(stored.getUserEmail()).isEqualTo("member@gmail.com");
        assertThat(stored.getFullName()).isEqualTo("Hong Gil Dong");
        assertThat(stored.getUserPassword()).isEqualTo("encoded");
        assertThat(stored.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(stored.getUserRole()).isEqualTo(UserRole.USER);
        assertThat(stored.getVerificationToken()).isEqualTo("generated-token");
        assertThat(stored.getProfileImage()).isEqualTo("uploads/default.png");
        assertThat(result.email()).isEqualTo("member@gmail.com");
        assertThat(result.verificationEmailRequested()).isTrue();
    }

    @Test
    void mailFailureKeepsTheInsertedInactiveUserAndReturnsRecoverableResult() {
        RegistrationForm form = validForm();
        when(passwordEncoder.encode("Password!")).thenReturn("encoded");
        when(emailVerificationService.requestInitialVerification(any())).thenReturn(false);

        RegistrationResult result = userService.registerUser(form);

        InOrder order = inOrder(userMapper, emailVerificationService);
        order.verify(userMapper).insertUser(any());
        order.verify(emailVerificationService).requestInitialVerification(any());
        assertThat(result.verificationEmailRequested()).isFalse();
    }

    @Test
    void duplicateEmailIsRejectedBeforePasswordEncodingOrInsert() {
        RegistrationForm form = validForm();
        when(userMapper.findByEmail("member@gmail.com")).thenReturn(new User());

        assertThatThrownBy(() -> userService.registerUser(form))
                .isInstanceOf(RegistrationValidationException.class)
                .extracting(exception -> ((RegistrationValidationException) exception).getField())
                .isEqualTo("userEmail");

        verify(passwordEncoder, never()).encode(any());
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void duplicateUsernameIsRejectedByTheFinalServerCheck() {
        RegistrationForm form = validForm();
        when(userMapper.countByUsername("member")).thenReturn(1);

        assertThatThrownBy(() -> userService.registerUser(form))
                .isInstanceOf(RegistrationValidationException.class)
                .extracting(exception -> ((RegistrationValidationException) exception).getField())
                .isEqualTo("username");

        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void duplicateNicknameIsRejectedByTheFinalServerCheck() {
        RegistrationForm form = validForm();
        when(userMapper.countByNickname("여행자123")).thenReturn(1);

        assertThatThrownBy(() -> userService.registerUser(form))
                .isInstanceOf(RegistrationValidationException.class)
                .extracting(exception -> ((RegistrationValidationException) exception).getField())
                .isEqualTo("nickname");

        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void databaseUniquenessRaceIsConvertedToSafeRegistrationError() {
        RegistrationForm form = validForm();
        when(passwordEncoder.encode("Password!")).thenReturn("encoded");
        doThrow(new DuplicateKeyException("duplicate")).when(userMapper).insertUser(any());

        assertThatThrownBy(() -> userService.registerUser(form))
                .isInstanceOf(RegistrationValidationException.class)
                .hasMessage("이미 사용 중인 회원가입 정보가 있습니다.");

        verify(emailVerificationService, never()).requestInitialVerification(any());
    }

    private RegistrationForm validForm() {
        RegistrationForm form = new RegistrationForm();
        form.setUsername("member");
        form.setUserEmail("member@gmail.com");
        form.setUserPassword("Password!");
        form.setPasswordConfirm("Password!");
        form.setNickname("여행자123");
        form.setFullName("여행자");
        form.setUserPhone("010-1234-5678");
        form.setUserBirth(LocalDate.of(1995, 5, 10));
        return form;
    }
}
