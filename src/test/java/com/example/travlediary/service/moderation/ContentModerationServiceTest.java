package com.example.travlediary.service.moderation;

import com.example.travlediary.dto.ContentModerationForm;
import com.example.travlediary.model.ContentModeration;
import com.example.travlediary.model.ModerationStatus;
import com.example.travlediary.model.ModerationTargetType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.moderation.ContentModerationMapper;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentModerationServiceTest {

    @Mock
    private ContentModerationMapper contentModerationMapper;
    @Mock
    private UserMapper userMapper;

    private ContentModerationService service;

    @BeforeEach
    void setUp() {
        service = new ContentModerationService(contentModerationMapper, userMapper);
    }

    /* === 숨김 === */

    @Test
    void hidingMarksTheContentDeletedAndRecordsTheModeration() {
        adminIsSignedIn();
        when(contentModerationMapper.findActiveTargetOwnerId(ModerationTargetType.POST, 3L))
                .thenReturn(9L);
        when(contentModerationMapper.hideTarget(ModerationTargetType.POST, 3L)).thenReturn(1);
        when(contentModerationMapper.insert(any(ContentModeration.class))).thenReturn(1);

        service.hide(ModerationTargetType.POST, 3L, form("욕설", "내부 메모"), 1L);

        ArgumentCaptor<ContentModeration> captor =
                ArgumentCaptor.forClass(ContentModeration.class);
        verify(contentModerationMapper).insert(captor.capture());
        ContentModeration saved = captor.getValue();
        assertThat(saved.getTargetType()).isEqualTo(ModerationTargetType.POST);
        assertThat(saved.getTargetId()).isEqualTo(3L);
        assertThat(saved.getTargetUserId()).isEqualTo(9L);
        assertThat(saved.getStatus()).isEqualTo(ModerationStatus.ACTIVE);
        assertThat(saved.getReason()).isEqualTo("욕설");
        assertThat(saved.getAdminNote()).isEqualTo("내부 메모");
        assertThat(saved.getCreatedBy()).isEqualTo(1L);
        verify(contentModerationMapper).hideTarget(ModerationTargetType.POST, 3L);
    }

    @Test
    void everySupportedTargetTypeCanBeHidden() {
        adminIsSignedIn();
        for (ModerationTargetType type : ModerationTargetType.values()) {
            when(contentModerationMapper.findActiveTargetOwnerId(type, 3L)).thenReturn(9L);
            when(contentModerationMapper.hideTarget(type, 3L)).thenReturn(1);
            when(contentModerationMapper.insert(any(ContentModeration.class))).thenReturn(1);

            service.hide(type, 3L, form("사유", null), 1L);

            verify(contentModerationMapper).hideTarget(type, 3L);
        }
    }

    @Test
    void hideReasonIsRequiredAndNoteIsOptional() {
        adminIsSignedIn();

        assertThatThrownBy(() -> service.hide(ModerationTargetType.POST, 3L, form("  ", null), 1L))
                .isInstanceOfSatisfying(ModerationValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("reason"));
        verify(contentModerationMapper, never()).hideTarget(any(), anyLong());

        when(contentModerationMapper.findActiveTargetOwnerId(ModerationTargetType.POST, 3L))
                .thenReturn(9L);
        when(contentModerationMapper.hideTarget(ModerationTargetType.POST, 3L)).thenReturn(1);
        when(contentModerationMapper.insert(any(ContentModeration.class))).thenReturn(1);

        service.hide(ModerationTargetType.POST, 3L, form("사유", "  "), 1L);

        ArgumentCaptor<ContentModeration> captor =
                ArgumentCaptor.forClass(ContentModeration.class);
        verify(contentModerationMapper).insert(captor.capture());
        assertThat(captor.getValue().getAdminNote()).isNull();
    }

    @Test
    void contentDeletedByItsOwnerCannotBeHidden() {
        adminIsSignedIn();
        // deleted = 1 이면 활성 대상 조회가 비어 있다
        when(contentModerationMapper.findActiveTargetOwnerId(ModerationTargetType.POST_COMMENT, 3L))
                .thenReturn(null);

        assertThatThrownBy(() -> service.hide(ModerationTargetType.POST_COMMENT, 3L,
                form("사유", null), 1L))
                .isInstanceOf(ModerationValidationException.class)
                .hasMessage("이미 삭제되었거나 조치된 콘텐츠입니다.");

        verify(contentModerationMapper, never()).hideTarget(any(), anyLong());
        verify(contentModerationMapper, never()).insert(any());
    }

    @Test
    void alreadyModeratedContentIsNotHiddenTwice() {
        adminIsSignedIn();
        when(contentModerationMapper.findActiveTargetOwnerId(ModerationTargetType.COURSE, 3L))
                .thenReturn(9L);
        when(contentModerationMapper.findActiveByTargetForUpdate(ModerationTargetType.COURSE, 3L))
                .thenReturn(activeModeration());

        assertThatThrownBy(() -> service.hide(ModerationTargetType.COURSE, 3L, form("사유", null), 1L))
                .isInstanceOf(ModerationValidationException.class)
                .hasMessage("이미 조치 중인 콘텐츠입니다.");

        verify(contentModerationMapper, never()).hideTarget(any(), anyLong());
    }

    /* === 복구 === */

    @Test
    void restoringUndeletesTheContentAndClosesTheModeration() {
        adminIsSignedIn();
        when(contentModerationMapper.findActiveByTargetForUpdate(ModerationTargetType.POST, 3L))
                .thenReturn(activeModeration());
        when(contentModerationMapper.findHiddenTargetOwnerId(ModerationTargetType.POST, 3L))
                .thenReturn(9L);
        when(contentModerationMapper.restoreTarget(ModerationTargetType.POST, 3L)).thenReturn(1);
        when(contentModerationMapper.restoreModeration(eq(50L), any(LocalDateTime.class),
                eq(1L), eq("오탐"))).thenReturn(1);

        service.restore(ModerationTargetType.POST, 3L, form("오탐", null), 1L);

        verify(contentModerationMapper).restoreTarget(ModerationTargetType.POST, 3L);
        verify(contentModerationMapper).restoreModeration(eq(50L), any(LocalDateTime.class),
                eq(1L), eq("오탐"));
    }

    @Test
    void contentDeletedByItsOwnerIsNeverRestored() {
        adminIsSignedIn();
        // 관리자 조치 이력이 없는 삭제 콘텐츠 = 사용자가 직접 지운 것
        when(contentModerationMapper.findActiveByTargetForUpdate(
                ModerationTargetType.COURSE_COMMENT, 3L)).thenReturn(null);

        assertThatThrownBy(() -> service.restore(ModerationTargetType.COURSE_COMMENT, 3L,
                form(null, null), 1L))
                .isInstanceOf(ModerationValidationException.class)
                .hasMessage("복구할 수 있는 관리자 조치가 없습니다.");

        verify(contentModerationMapper, never()).restoreTarget(any(), anyLong());
        verify(contentModerationMapper, never()).restoreModeration(anyLong(), any(), anyLong(), any());
    }

    @Test
    void restoreStopsWhenTheContentIsNoLongerHidden() {
        adminIsSignedIn();
        when(contentModerationMapper.findActiveByTargetForUpdate(ModerationTargetType.POST, 3L))
                .thenReturn(activeModeration());
        when(contentModerationMapper.findHiddenTargetOwnerId(ModerationTargetType.POST, 3L))
                .thenReturn(null);

        assertThatThrownBy(() -> service.restore(ModerationTargetType.POST, 3L, form(null, null), 1L))
                .isInstanceOf(ModerationValidationException.class);

        verify(contentModerationMapper, never()).restoreTarget(any(), anyLong());
    }

    /* === 권한 === */

    @Test
    void onlyAdminsCanModerateEvenIfTheRequestReachesTheService() {
        User member = new User();
        member.setId(2L);
        member.setUserRole(UserRole.USER);
        when(userMapper.findById(2L)).thenReturn(member);

        assertThatThrownBy(() -> service.hide(ModerationTargetType.POST, 3L, form("사유", null), 2L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.restore(ModerationTargetType.POST, 3L, form(null, null), 2L))
                .isInstanceOf(ResponseStatusException.class);

        verify(contentModerationMapper, never()).hideTarget(any(), anyLong());
        verify(contentModerationMapper, never()).restoreTarget(any(), anyLong());
    }

    @Test
    void anonymousRequestsAreRejected() {
        assertThatThrownBy(() -> service.hide(ModerationTargetType.POST, 3L, form("사유", null), null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void hideAndRestoreRunInOneWriteTransaction() throws NoSuchMethodException {
        for (String name : new String[]{"hide", "restore"}) {
            Transactional transactional = ContentModerationService.class
                    .getMethod(name, ModerationTargetType.class, Long.class,
                            ContentModerationForm.class, Long.class)
                    .getAnnotation(Transactional.class);
            assertThat(transactional).as(name).isNotNull();
            assertThat(transactional.readOnly()).isFalse();
        }
    }

    private void adminIsSignedIn() {
        User admin = new User();
        admin.setId(1L);
        admin.setUserRole(UserRole.ADMIN);
        when(userMapper.findById(1L)).thenReturn(admin);
    }

    private ContentModeration activeModeration() {
        ContentModeration moderation = new ContentModeration();
        moderation.setId(50L);
        moderation.setTargetType(ModerationTargetType.POST);
        moderation.setTargetId(3L);
        moderation.setTargetUserId(9L);
        moderation.setStatus(ModerationStatus.ACTIVE);
        moderation.setReason("욕설");
        return moderation;
    }

    private ContentModerationForm form(String reason, String adminNote) {
        ContentModerationForm form = new ContentModerationForm();
        form.setReason(reason);
        form.setAdminNote(adminNote);
        return form;
    }
}
