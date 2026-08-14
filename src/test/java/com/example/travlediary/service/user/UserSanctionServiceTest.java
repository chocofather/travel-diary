package com.example.travlediary.service.user;

import com.example.travlediary.dto.SanctionReleaseForm;
import com.example.travlediary.dto.UserSanctionForm;
import com.example.travlediary.model.BlockedEmail;
import com.example.travlediary.model.SanctionReleaseVia;
import com.example.travlediary.model.SanctionStatus;
import com.example.travlediary.model.SanctionType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserSanction;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.BlockedEmailMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.repository.user.UserSanctionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSanctionServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserSanctionMapper userSanctionMapper;
    @Mock
    private BlockedEmailMapper blockedEmailMapper;
    @Mock
    private EmailHasher emailHasher;

    private UserSanctionService service;

    @BeforeEach
    void setUp() {
        service = new UserSanctionService(userMapper, userSanctionMapper,
                blockedEmailMapper, emailHasher);
    }

    /* === 이용제한 적용 === */

    @Test
    void temporaryRestrictionStoresSanctionAndSwitchesUserStatusTogether() {
        User target = user(UserStatus.ACTIVE);
        when(userMapper.findByIdForUpdate(5L)).thenReturn(target);
        when(userSanctionMapper.insert(any(UserSanction.class))).thenReturn(1);
        when(userMapper.updateStatusForAdmin(5L, UserStatus.RESTRICTED, UserStatus.ACTIVE))
                .thenReturn(1);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        service.restrict(5L, temporaryForm(expiresAt), 1L);

        ArgumentCaptor<UserSanction> captor = ArgumentCaptor.forClass(UserSanction.class);
        verify(userSanctionMapper).insert(captor.capture());
        UserSanction saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(5L);
        assertThat(saved.getType()).isEqualTo(SanctionType.TEMPORARY);
        assertThat(saved.getStatus()).isEqualTo(SanctionStatus.ACTIVE);
        assertThat(saved.getReason()).isEqualTo("이용약관 위반");
        assertThat(saved.getAdminNote()).isEqualTo("내부 메모");
        assertThat(saved.getPreviousStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(saved.getCreatedBy()).isEqualTo(1L);
        verify(userMapper).updateStatusForAdmin(5L, UserStatus.RESTRICTED, UserStatus.ACTIVE);
        verify(blockedEmailMapper, never()).insert(any());
    }

    @Test
    void permanentRestrictionKeepsNullExpiryAndBlocksTheRegistrationEmail() {
        User target = user(UserStatus.ACTIVE);
        when(userMapper.findByIdForUpdate(5L)).thenReturn(target);
        when(userSanctionMapper.insert(any(UserSanction.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, UserSanction.class).setId(77L);
            return 1;
        });
        when(userMapper.updateStatusForAdmin(5L, UserStatus.RESTRICTED, UserStatus.ACTIVE))
                .thenReturn(1);
        when(emailHasher.hash("user@example.com")).thenReturn("hashed-value");

        service.restrict(5L, permanentForm(), 1L);

        ArgumentCaptor<UserSanction> sanctionCaptor = ArgumentCaptor.forClass(UserSanction.class);
        verify(userSanctionMapper).insert(sanctionCaptor.capture());
        assertThat(sanctionCaptor.getValue().getExpiresAt()).isNull();

        ArgumentCaptor<BlockedEmail> blockCaptor = ArgumentCaptor.forClass(BlockedEmail.class);
        verify(blockedEmailMapper).insert(blockCaptor.capture());
        BlockedEmail blocked = blockCaptor.getValue();
        assertThat(blocked.getEmailHash()).isEqualTo("hashed-value");
        assertThat(blocked.getSanctionId()).isEqualTo(77L);
        assertThat(blocked.getUserId()).isEqualTo(5L);
    }

    @Test
    void suspendedUserCanBeRestrictedAndKeepsPreviousStatusForRestore() {
        User target = user(UserStatus.SUSPENDED);
        when(userMapper.findByIdForUpdate(5L)).thenReturn(target);
        when(userSanctionMapper.insert(any(UserSanction.class))).thenReturn(1);
        when(userMapper.updateStatusForAdmin(5L, UserStatus.RESTRICTED, UserStatus.SUSPENDED))
                .thenReturn(1);

        service.restrict(5L, permanentForm(), 1L);

        ArgumentCaptor<UserSanction> captor = ArgumentCaptor.forClass(UserSanction.class);
        verify(userSanctionMapper).insert(captor.capture());
        assertThat(captor.getValue().getPreviousStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void adminAccountsAreNeverRestricted() {
        User admin = user(UserStatus.ACTIVE);
        admin.setUserRole(UserRole.ADMIN);
        when(userMapper.findByIdForUpdate(5L)).thenReturn(admin);

        assertThatThrownBy(() -> service.restrict(5L, permanentForm(), 1L))
                .isInstanceOf(SanctionValidationException.class)
                .hasMessage("관리자 계정은 이용제한 대상이 아닙니다.");

        verify(userSanctionMapper, never()).insert(any());
        verify(userMapper, never()).updateStatusForAdmin(any(), any(), any());
    }

    @Test
    void duplicateActiveSanctionIsRejected() {
        User target = user(UserStatus.ACTIVE);
        when(userMapper.findByIdForUpdate(5L)).thenReturn(target);
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L)).thenReturn(activeSanction());

        assertThatThrownBy(() -> service.restrict(5L, permanentForm(), 1L))
                .isInstanceOf(SanctionValidationException.class)
                .hasMessage("이미 적용 중인 이용제한이 있습니다.");

        verify(userSanctionMapper, never()).insert(any());
    }

    @Test
    void onlyActiveOrSuspendedUsersCanBeRestricted() {
        for (UserStatus status : List.of(UserStatus.INACTIVE, UserStatus.DEACTIVATED,
                UserStatus.RESTRICTED)) {
            User target = user(status);
            when(userMapper.findByIdForUpdate(5L)).thenReturn(target);

            assertThatThrownBy(() -> service.restrict(5L, permanentForm(), 1L))
                    .isInstanceOf(SanctionValidationException.class);
        }
        verify(userSanctionMapper, never()).insert(any());
    }

    @Test
    void temporaryRestrictionRequiresAFutureExpiry() {
        User target = user(UserStatus.ACTIVE);
        when(userMapper.findByIdForUpdate(5L)).thenReturn(target);

        assertThatThrownBy(() -> service.restrict(5L, temporaryForm(null), 1L))
                .isInstanceOfSatisfying(SanctionValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("expiresAt"));

        assertThatThrownBy(() -> service.restrict(5L,
                temporaryForm(LocalDateTime.now().minusMinutes(1)), 1L))
                .isInstanceOfSatisfying(SanctionValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("expiresAt"));

        verify(userSanctionMapper, never()).insert(any());
    }

    @Test
    void reasonIsRequired() {
        User target = user(UserStatus.ACTIVE);
        when(userMapper.findByIdForUpdate(5L)).thenReturn(target);
        UserSanctionForm form = permanentForm();
        form.setReason("   ");

        assertThatThrownBy(() -> service.restrict(5L, form, 1L))
                .isInstanceOfSatisfying(SanctionValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("reason"));

        verify(userSanctionMapper, never()).insert(any());
    }

    @Test
    void missingUserReturnsNotFound() {
        when(userMapper.findByIdForUpdate(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.restrict(99L, permanentForm(), 1L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    /* === 해제 === */

    @Test
    void releaseLiftsTheSanctionAndRestoresThePreviousStatus() {
        User target = user(UserStatus.RESTRICTED);
        UserSanction sanction = activeSanction();
        when(userMapper.findByIdForUpdate(5L)).thenReturn(target);
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L)).thenReturn(sanction);
        when(userSanctionMapper.release(eq(10L), eq(SanctionStatus.LIFTED), any(),
                eq(1L), eq(SanctionReleaseVia.ADMIN), eq("소명 완료"))).thenReturn(1);
        when(userMapper.updateStatusForAdmin(5L, UserStatus.ACTIVE, UserStatus.RESTRICTED))
                .thenReturn(1);

        SanctionReleaseForm form = new SanctionReleaseForm();
        form.setReleaseReason("소명 완료");
        service.release(5L, form, 1L);

        verify(userSanctionMapper).release(eq(10L), eq(SanctionStatus.LIFTED), any(),
                eq(1L), eq(SanctionReleaseVia.ADMIN), eq("소명 완료"));
        verify(userMapper).updateStatusForAdmin(5L, UserStatus.ACTIVE, UserStatus.RESTRICTED);
    }

    @Test
    void releaseDoesNotRestoreStatusWhenUserIsNoLongerRestricted() {
        User target = user(UserStatus.DEACTIVATED);
        UserSanction sanction = activeSanction();
        when(userMapper.findByIdForUpdate(5L)).thenReturn(target);
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L)).thenReturn(sanction);
        when(userSanctionMapper.release(eq(10L), eq(SanctionStatus.LIFTED), any(),
                eq(1L), eq(SanctionReleaseVia.ADMIN), eq(null))).thenReturn(1);

        service.release(5L, new SanctionReleaseForm(), 1L);

        verify(userMapper, never()).updateStatusForAdmin(any(), any(), any());
    }

    @Test
    void releasingAPermanentSanctionAlsoReleasesTheEmailBlock() {
        User target = user(UserStatus.RESTRICTED);
        UserSanction sanction = activeSanction();
        sanction.setType(SanctionType.PERMANENT);
        sanction.setExpiresAt(null);
        when(userMapper.findByIdForUpdate(5L)).thenReturn(target);
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L)).thenReturn(sanction);
        when(userSanctionMapper.release(eq(10L), eq(SanctionStatus.LIFTED), any(),
                eq(1L), eq(SanctionReleaseVia.ADMIN), eq(null))).thenReturn(1);
        when(userMapper.updateStatusForAdmin(5L, UserStatus.ACTIVE, UserStatus.RESTRICTED))
                .thenReturn(1);

        service.release(5L, new SanctionReleaseForm(), 1L);

        verify(blockedEmailMapper).releaseBySanctionId(eq(10L), any(), eq(1L));
    }

    @Test
    void releaseWithoutActiveSanctionIsRejected() {
        when(userMapper.findByIdForUpdate(5L)).thenReturn(user(UserStatus.ACTIVE));
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.release(5L, new SanctionReleaseForm(), 1L))
                .isInstanceOf(SanctionValidationException.class)
                .hasMessage("적용 중인 이용제한이 없습니다.");
    }

    @Test
    void adminAccountsAreNeverReleased() {
        User admin = user(UserStatus.RESTRICTED);
        admin.setUserRole(UserRole.ADMIN);
        when(userMapper.findByIdForUpdate(5L)).thenReturn(admin);

        assertThatThrownBy(() -> service.release(5L, new SanctionReleaseForm(), 1L))
                .isInstanceOf(SanctionValidationException.class);

        verify(userSanctionMapper, never()).release(any(), any(), any(), any(), any(), any());
    }

    /* === 자동 만료 === */

    @Test
    void batchExpiresDueTemporarySanctionsAsSystemRelease() {
        UserSanction due = activeSanction();
        due.setExpiresAt(LocalDateTime.now().minusHours(1));
        LocalDateTime now = LocalDateTime.now();
        when(userSanctionMapper.findExpiredActiveSanctions(now)).thenReturn(List.of(due));
        when(userMapper.findByIdForUpdate(5L)).thenReturn(user(UserStatus.RESTRICTED));
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L)).thenReturn(due);
        when(userSanctionMapper.release(eq(10L), eq(SanctionStatus.EXPIRED), any(),
                eq(null), eq(SanctionReleaseVia.SYSTEM), eq(null))).thenReturn(1);
        when(userMapper.updateStatusForAdmin(5L, UserStatus.ACTIVE, UserStatus.RESTRICTED))
                .thenReturn(1);

        assertThat(service.expireDueSanctions(now)).isEqualTo(1);
        verify(userMapper).updateStatusForAdmin(5L, UserStatus.ACTIVE, UserStatus.RESTRICTED);
    }

    @Test
    void loginTimeCheckReleasesAnAlreadyExpiredTemporarySanction() {
        UserSanction due = activeSanction();
        due.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L)).thenReturn(due);
        when(userMapper.findByIdForUpdate(5L)).thenReturn(user(UserStatus.RESTRICTED));
        when(userSanctionMapper.release(eq(10L), eq(SanctionStatus.EXPIRED), any(),
                eq(null), eq(SanctionReleaseVia.SYSTEM), eq(null))).thenReturn(1);
        when(userMapper.updateStatusForAdmin(5L, UserStatus.ACTIVE, UserStatus.RESTRICTED))
                .thenReturn(1);

        assertThat(service.releaseIfExpired(5L)).isTrue();
    }

    @Test
    void loginTimeCheckKeepsRunningTemporaryAndPermanentSanctions() {
        UserSanction running = activeSanction();
        running.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L)).thenReturn(running);

        assertThat(service.releaseIfExpired(5L)).isFalse();

        UserSanction permanent = activeSanction();
        permanent.setType(SanctionType.PERMANENT);
        permanent.setExpiresAt(null);
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L)).thenReturn(permanent);

        assertThat(service.releaseIfExpired(5L)).isFalse();
        verify(userSanctionMapper, never()).release(any(), any(), any(), any(), any(), any());
    }

    @Test
    void loginTimeCheckIsNoOpWithoutActiveSanction() {
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L)).thenReturn(null);

        assertThat(service.releaseIfExpired(5L)).isFalse();
    }

    private User user(UserStatus status) {
        User user = new User();
        user.setId(5L);
        user.setUsername("travler");
        user.setUserEmail("user@example.com");
        user.setUserRole(UserRole.USER);
        user.setStatus(status);
        return user;
    }

    private UserSanction activeSanction() {
        UserSanction sanction = new UserSanction();
        sanction.setId(10L);
        sanction.setUserId(5L);
        sanction.setType(SanctionType.TEMPORARY);
        sanction.setStatus(SanctionStatus.ACTIVE);
        sanction.setReason("이용약관 위반");
        sanction.setPreviousStatus(UserStatus.ACTIVE);
        sanction.setExpiresAt(LocalDateTime.now().plusDays(3));
        return sanction;
    }

    private UserSanctionForm temporaryForm(LocalDateTime expiresAt) {
        UserSanctionForm form = new UserSanctionForm();
        form.setType(SanctionType.TEMPORARY);
        form.setReason("이용약관 위반");
        form.setAdminNote("내부 메모");
        form.setExpiresAt(expiresAt);
        return form;
    }

    private UserSanctionForm permanentForm() {
        UserSanctionForm form = new UserSanctionForm();
        form.setType(SanctionType.PERMANENT);
        form.setReason("이용약관 위반");
        return form;
    }
}