package com.example.travlediary.service.user;

import com.example.travlediary.dto.MyPageProfileDto;
import com.example.travlediary.dto.ProfileUpdateForm;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.file.ProfileImageStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyPageServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private ProfileImageStorageService profileImageStorageService;

    private MyPageService service;

    @BeforeEach
    void setUp() {
        service = new MyPageService(userMapper, profileImageStorageService);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void myPageDtoContainsOnlyFieldsNeededByTheScreen() {
        assertThat(Arrays.stream(MyPageProfileDto.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .containsExactlyInAnyOrder("nickname", "userEmail", "profileImage");
        assertThat(Arrays.stream(ProfileUpdateForm.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .containsExactlyInAnyOrder("nickname", "profileImageFile");
    }

    @Test
    void returnsProfileWithLegacyImagePathsNormalized() {
        MyPageProfileDto profile = profile("여행자", "uploads/default.png");
        when(userMapper.findMyPageProfileById(7L)).thenReturn(profile);

        MyPageProfileDto result = service.getProfile(7L);

        assertThat(result.getNickname()).isEqualTo("여행자");
        assertThat(result.getUserEmail()).isEqualTo("traveler@example.com");
        assertThat(result.getProfileImage()).isEqualTo("/images/default.png");
    }

    @Test
    void stripsNicknameAndAllowsKeepingTheCurrentNickname() {
        when(userMapper.findMyPageProfileByIdForUpdate(7L)).thenReturn(profile("기존닉네임", "/images/default.png"));
        when(userMapper.countByNicknameExcludingUserId("기존닉네임", 7L)).thenReturn(0);
        ProfileUpdateForm form = form("  기존닉네임  ");

        service.updateProfile(7L, form);

        assertThat(form.getNickname()).isEqualTo("기존닉네임");
        verify(userMapper).updateMyPageProfile(7L, "기존닉네임", null);
    }

    @Test
    void asyncNicknameCheckUsesTheSamePolicyAndExcludesCurrentUser() {
        when(userMapper.findMyPageProfileById(7L)).thenReturn(profile("기존닉네임", null));
        when(userMapper.countByNicknameExcludingUserId("여행민준", 7L)).thenReturn(0);
        when(userMapper.countByNicknameExcludingUserId("중복닉네임", 7L)).thenReturn(1);

        assertThat(service.checkNickname(7L, "  여행민준  "))
                .isEqualTo(NicknameCheckStatus.AVAILABLE);
        assertThat(service.checkNickname(7L, "중복닉네임"))
                .isEqualTo(NicknameCheckStatus.DUPLICATE);

        verify(userMapper).countByNicknameExcludingUserId("여행민준", 7L);
        verify(userMapper).countByNicknameExcludingUserId("중복닉네임", 7L);
    }

    @Test
    void asyncNicknameCheckRejectsInvalidFormatBeforeQueryingDatabase() {
        assertThatThrownBy(() -> service.checkNickname(7L, "여행 민준"))
                .isInstanceOfSatisfying(NicknamePolicy.ViolationException.class, exception ->
                        assertThat(exception.getViolationType())
                                .isEqualTo(NicknamePolicy.ViolationType.INVALID_FORMAT));

        verify(userMapper, never()).findMyPageProfileById(any());
        verify(userMapper, never()).countByNicknameExcludingUserId(any(), any());
    }

    @Test
    void asyncNicknameCheckReturnsCurrentBeforeApplyingNewForbiddenPolicy() {
        when(userMapper.findMyPageProfileById(7L)).thenReturn(profile("관리자", null));

        assertThat(service.checkNickname(7L, "관리자"))
                .isEqualTo(NicknameCheckStatus.CURRENT);

        verify(userMapper, never()).countByNicknameExcludingUserId(any(), any());
    }

    @Test
    void asyncNicknameCheckRejectsForbiddenNameBeforeDuplicateQuery() {
        when(userMapper.findMyPageProfileById(7L)).thenReturn(profile("기존닉네임", null));

        assertThatThrownBy(() -> service.checkNickname(7L, "병12신"))
                .isInstanceOfSatisfying(NicknamePolicy.ViolationException.class, exception -> {
                    assertThat(exception.getViolationType())
                            .isEqualTo(NicknamePolicy.ViolationType.FORBIDDEN);
                    assertThat(exception.getMessage()).isEqualTo(NicknamePolicy.FORBIDDEN_MESSAGE);
                });

        verify(userMapper, never()).countByNicknameExcludingUserId(any(), any());
    }

    @Test
    void rejectsBlankShortLongAndOtherUsersNickname() {
        when(userMapper.findMyPageProfileByIdForUpdate(7L)).thenReturn(profile("기존닉네임", null));

        assertNicknamePolicyError(() -> service.updateProfile(7L, form("   ")));
        assertNicknamePolicyError(() -> service.updateProfile(7L, form("한")));
        assertNicknamePolicyError(() -> service.updateProfile(7L, form("가".repeat(13))));
        assertNicknamePolicyError(() -> service.updateProfile(7L, form("여행 민준")));
        assertNicknamePolicyError(() -> service.updateProfile(7L, form("민준!")));
        assertNicknamePolicyError(() -> service.updateProfile(7L, form("min_jun")));

        when(userMapper.countByNicknameExcludingUserId("중복닉네임", 7L)).thenReturn(1);
        assertFieldError("nickname", "이미 사용", () -> service.updateProfile(7L, form("중복닉네임")));
        verify(userMapper, never()).updateMyPageProfile(any(), any(), any());
    }

    @Test
    void profileUpdateRejectsForbiddenNicknameBeforeDuplicateQuery() {
        when(userMapper.findMyPageProfileByIdForUpdate(7L))
                .thenReturn(profile("기존닉네임", null));

        assertFieldError("nickname", NicknamePolicy.FORBIDDEN_MESSAGE,
                () -> service.updateProfile(7L, form("a1d2m3i4n")));

        verify(userMapper, never()).countByNicknameExcludingUserId(any(), any());
        verify(userMapper, never()).updateMyPageProfile(any(), any(), any());
    }

    @Test
    void existingForbiddenNicknameCanRemainForAnImageOnlyUpdate() {
        String oldImage = "/uploads/profiles/11111111-1111-4111-8111-111111111111.jpg";
        String newImage = "/uploads/profiles/22222222-2222-4222-8222-222222222222.png";
        when(userMapper.findMyPageProfileByIdForUpdate(7L))
                .thenReturn(profile("관리자", oldImage));
        when(profileImageStorageService.saveProfileImage(any())).thenReturn(newImage);
        ProfileUpdateForm form = form("관리자");
        form.setProfileImageFile(image());

        service.updateProfile(7L, form);

        verify(userMapper).updateMyPageProfile(7L, "관리자", newImage);
    }

    @Test
    void translatesDatabaseUniqueRaceIntoNicknameValidationError() {
        when(userMapper.findMyPageProfileByIdForUpdate(7L)).thenReturn(profile("기존닉네임", null));
        when(userMapper.countByNicknameExcludingUserId("새닉네임", 7L)).thenReturn(0);
        when(userMapper.updateMyPageProfile(7L, "새닉네임", null))
                .thenThrow(new DuplicateKeyException("nickname_UNIQUE"));

        assertFieldError("nickname", "이미 사용",
                () -> service.updateProfile(7L, form("새닉네임")));
    }

    @Test
    void anEmptyImageSelectionKeepsTheExistingDatabaseValue() {
        when(userMapper.findMyPageProfileByIdForUpdate(7L))
                .thenReturn(profile("기존닉네임", "/uploads/profiles/existing.jpg"));
        ProfileUpdateForm form = form("기존닉네임");
        form.setProfileImageFile(new MockMultipartFile(
                "profileImageFile", "", "application/octet-stream", new byte[0]));

        service.updateProfile(7L, form);

        verify(userMapper).updateMyPageProfile(7L, "기존닉네임", null);
        verify(profileImageStorageService, never()).saveProfileImage(any());
        verify(profileImageStorageService, never()).deleteManagedProfileImage(any());
    }

    @Test
    void imageValidationFailureIsReportedOnTheImageField() {
        when(userMapper.findMyPageProfileByIdForUpdate(7L))
                .thenReturn(profile("기존닉네임", "/images/default.png"));
        ProfileUpdateForm form = form("기존닉네임");
        form.setProfileImageFile(image());
        when(profileImageStorageService.saveProfileImage(any()))
                .thenThrow(new IllegalArgumentException(
                        "프로필 이미지는 JPG, PNG, WEBP 형식만 사용할 수 있습니다."));

        assertFieldError("profileImageFile", "JPG, PNG, WEBP",
                () -> service.updateProfile(7L, form));

        verify(userMapper, never()).updateMyPageProfile(any(), any(), any());
    }

    @Test
    void databaseFailureRemovesTheNewFileAndKeepsThePreviousFile() {
        String oldImage = "/uploads/profiles/11111111-1111-4111-8111-111111111111.jpg";
        String newImage = "/uploads/profiles/22222222-2222-4222-8222-222222222222.png";
        when(userMapper.findMyPageProfileByIdForUpdate(7L)).thenReturn(profile("기존닉네임", oldImage));
        when(profileImageStorageService.saveProfileImage(any())).thenReturn(newImage);
        when(userMapper.updateMyPageProfile(7L, "새닉네임", newImage))
                .thenThrow(new DataAccessResourceFailureException("db failure"));
        ProfileUpdateForm form = form("새닉네임");
        form.setProfileImageFile(image());

        assertThatThrownBy(() -> service.updateProfile(7L, form))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(profileImageStorageService).deleteManagedProfileImage(newImage);
        verify(profileImageStorageService, never()).deleteManagedProfileImage(oldImage);
    }

    @Test
    void commitDeletesOnlyThePreviousManagedImageAndRollbackDeletesOnlyTheNewImage() {
        String oldImage = "/uploads/profiles/11111111-1111-4111-8111-111111111111.jpg";
        String newImage = "/uploads/profiles/22222222-2222-4222-8222-222222222222.png";
        when(userMapper.findMyPageProfileByIdForUpdate(7L)).thenReturn(profile("기존닉네임", oldImage));
        when(profileImageStorageService.saveProfileImage(any())).thenReturn(newImage);
        ProfileUpdateForm form = form("새닉네임");
        form.setProfileImageFile(image());

        TransactionSynchronizationManager.initSynchronization();
        service.updateProfile(7L, form);
        verify(profileImageStorageService, never()).deleteManagedProfileImage(any());
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(profileImageStorageService).deleteManagedProfileImage(oldImage);
        verify(profileImageStorageService, never()).deleteManagedProfileImage(newImage);

        TransactionSynchronizationManager.initSynchronization();
        service.updateProfile(7L, form);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(profileImageStorageService).deleteManagedProfileImage(newImage);
    }

    private void assertFieldError(String field, String message,
                                  org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ProfileValidationException.class, exception -> {
                    assertThat(exception.getField()).isEqualTo(field);
                    assertThat(exception.getMessage()).contains(message);
                });
    }

    private void assertNicknamePolicyError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertFieldError("nickname", NicknamePolicy.INVALID_MESSAGE, callable);
    }

    private MyPageProfileDto profile(String nickname, String image) {
        MyPageProfileDto profile = new MyPageProfileDto();
        profile.setNickname(nickname);
        profile.setUserEmail("traveler@example.com");
        profile.setProfileImage(image);
        return profile;
    }

    private ProfileUpdateForm form(String nickname) {
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setNickname(nickname);
        return form;
    }

    private MockMultipartFile image() {
        return new MockMultipartFile(
                "profileImageFile", "avatar.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});
    }

    private void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
    }
}
