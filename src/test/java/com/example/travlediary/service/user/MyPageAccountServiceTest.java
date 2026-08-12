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
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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

    private MyPageAccountService service;

    @BeforeEach
    void setUp() {
        service = new MyPageAccountService(
                userMapper, bookmarkMapper, commentLikeMapper,
                postCommentMapper, courseCommentMapper, passwordEncoder);
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
        form.setUserBirth(LocalDate.of(2000, 1, 2));
        when(userMapper.updateAccountDetails(
                7L, "여행 민준", "010-1234-5678", LocalDate.of(2000, 1, 2)))
                .thenReturn(1);

        service.updateAccountDetails(7L, form);

        verify(userMapper).updateAccountDetails(
                7L, "여행 민준", "010-1234-5678", LocalDate.of(2000, 1, 2));
        assertThat(form.getFullName()).isEqualTo("여행 민준");
        assertThat(form.getUserPhone()).isEqualTo("010-1234-5678");
    }

    @Test
    void rejectsInvalidDetailsBeforeUpdate() {
        AccountEditForm form = new AccountEditForm();
        form.setFullName(" ");
        form.setUserBirth(LocalDate.now());

        assertThatThrownBy(() -> service.updateAccountDetails(7L, form))
                .isInstanceOf(AccountValidationException.class)
                .hasMessage("이름을 입력해주세요.");

        verify(userMapper, never()).updateAccountDetails(
                org.mockito.ArgumentMatchers.anyLong(), anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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

    @Test
    void wrongWithdrawalPasswordMakesNoMutation() {
        User account = activeAccount(UserRole.USER);
        when(userMapper.findActiveAccountSecurityByIdForUpdate(7L)).thenReturn(account);
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> service.withdraw(7L, "wrong"))
                .isInstanceOf(AccountValidationException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");

        verifyNoInteractions(bookmarkMapper, commentLikeMapper,
                postCommentMapper, courseCommentMapper);
        verify(userMapper, never()).deactivateAccount(
                org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminWithdrawalIsRejectedBeforePasswordOrMutation() {
        when(userMapper.findActiveAccountSecurityByIdForUpdate(99L))
                .thenReturn(activeAccount(UserRole.ADMIN));

        assertThatThrownBy(() -> service.withdraw(99L, "Password!"))
                .isInstanceOf(AccountValidationException.class)
                .hasMessage("관리자 계정은 마이페이지에서 탈퇴할 수 없습니다.");

        verifyNoInteractions(passwordEncoder, bookmarkMapper, commentLikeMapper,
                postCommentMapper, courseCommentMapper);
    }

    @Test
    void withdrawalClearsPrivateActivityAndReleasesEmailAndNicknameOnly() {
        User account = activeAccount(UserRole.USER);
        when(userMapper.findActiveAccountSecurityByIdForUpdate(7L)).thenReturn(account);
        when(passwordEncoder.matches("Password!", "encoded-password")).thenReturn(true);
        when(userMapper.deactivateAccount(
                org.mockito.ArgumentMatchers.eq(7L), anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq(UserStatus.DEACTIVATED))).thenReturn(1);

        service.withdraw(7L, "Password!");

        verify(commentLikeMapper).decrementDestinationLikeCountsByUserId(7L);
        verify(commentLikeMapper).deleteAllByUserId(7L);
        verify(postCommentMapper).deleteAllLikesByUserId(7L);
        verify(courseCommentMapper).deleteAllLikesByUserId(7L);
        verify(bookmarkMapper).deleteAllByUserId(7L);

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nickname = ArgumentCaptor.forClass(String.class);
        verify(userMapper).deactivateAccount(
                org.mockito.ArgumentMatchers.eq(7L), email.capture(), nickname.capture(),
                org.mockito.ArgumentMatchers.eq(UserStatus.DEACTIVATED));

        assertThat(email.getValue())
                .startsWith("withdrawn-7-")
                .endsWith("@example.invalid")
                .doesNotContain("member@example.com")
                .hasSizeLessThanOrEqualTo(100);
        assertThat(nickname.getValue())
                .startsWith("탈퇴")
                .hasSize(12)
                .matches("[가-힣A-Za-z0-9]+");
    }

    @Test
    void withdrawalRunsInOneWriteTransaction() throws NoSuchMethodException {
        Transactional transactional = MyPageAccountService.class
                .getMethod("withdraw", Long.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
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
