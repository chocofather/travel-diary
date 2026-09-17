package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryRestoreForm;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryItemStatus;
import com.example.travlediary.model.DiaryCoverLibraryModerationAction;
import com.example.travlediary.model.DiaryCoverLibraryModerationActionType;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAssetStatus;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.diary.DiaryCoverLibraryElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryItemMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryModerationActionMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryPhotoAssetMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryReportMapper;
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

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryCoverLibraryRestoreServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-17T03:00:00Z");

    @Mock private DiaryCoverLibraryReportMapper reportMapper;
    @Mock private DiaryCoverLibraryModerationActionMapper actionMapper;
    @Mock private DiaryCoverLibraryItemMapper itemMapper;
    @Mock private DiaryCoverLibraryPhotoAssetMapper photoAssetMapper;
    @Mock private DiaryCoverLibraryElementMapper elementMapper;
    @Mock private UserMapper userMapper;

    private DiaryCoverLibraryModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DiaryCoverLibraryModerationServiceImpl(
                reportMapper, actionMapper, itemMapper, photoAssetMapper,
                elementMapper, userMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void blockedPublishedItemRestoresItsPreviousStatusAndOriginalReportLink() {
        adminIsSignedIn();
        when(itemMapper.findByIdForUpdate(11L)).thenReturn(blockedItem());
        when(actionMapper.findLatestBlockItemByLibraryItemId(11L))
                .thenReturn(blockItemAction("PUBLISHED"));
        when(itemMapper.restoreByAdmin(11L, DiaryCoverLibraryItemStatus.PUBLISHED))
                .thenReturn(1);
        when(actionMapper.insert(any())).thenReturn(1);

        Long reportId = service.restoreItem(1L, 11L, restoreForm("오차단 확인", "재검토"));

        assertThat(reportId).isEqualTo(50L);
        verify(itemMapper).restoreByAdmin(11L, DiaryCoverLibraryItemStatus.PUBLISHED);
        DiaryCoverLibraryModerationAction action = capturedAction();
        assertThat(action.getReportId()).isEqualTo(50L);
        assertThat(action.getActionType())
                .isEqualTo(DiaryCoverLibraryModerationActionType.RESTORE_ITEM);
        assertThat(action.getPreviousStatus()).isEqualTo("BLOCKED");
        assertThat(action.getResultingStatus()).isEqualTo("PUBLISHED");
        assertThat(action.getReason()).isEqualTo("오차단 확인");
        assertThat(action.getAdminNote()).isEqualTo("재검토");
        verifyNoInteractions(reportMapper);
    }

    @Test
    void blockedWithdrawnItemRestoresToWithdrawn() {
        adminIsSignedIn();
        when(itemMapper.findByIdForUpdate(11L)).thenReturn(blockedItem());
        when(actionMapper.findLatestBlockItemByLibraryItemId(11L))
                .thenReturn(blockItemAction("WITHDRAWN"));
        when(itemMapper.restoreByAdmin(11L, DiaryCoverLibraryItemStatus.WITHDRAWN))
                .thenReturn(1);
        when(actionMapper.insert(any())).thenReturn(1);

        service.restoreItem(1L, 11L, restoreForm("작성자 공개중지 상태 복원", null));

        verify(itemMapper).restoreByAdmin(11L, DiaryCoverLibraryItemStatus.WITHDRAWN);
        assertThat(capturedAction().getResultingStatus()).isEqualTo("WITHDRAWN");
    }

    @Test
    void blockedPhotoRestoresToActiveAndReusesTheBlockingReport() {
        adminIsSignedIn();
        when(photoAssetMapper.findByIdForUpdate(701L)).thenReturn(blockedAsset());
        when(actionMapper.findLatestBlockPhotoByPhotoAssetId(701L))
                .thenReturn(blockPhotoAction());
        when(photoAssetMapper.restoreByAdmin(701L)).thenReturn(1);
        when(actionMapper.insert(any())).thenReturn(1);

        Long reportId = service.restorePhoto(
                1L, 701L, restoreForm("권리 관계 해결", null));

        assertThat(reportId).isEqualTo(51L);
        verify(photoAssetMapper).restoreByAdmin(701L);
        DiaryCoverLibraryModerationAction action = capturedAction();
        assertThat(action.getReportId()).isEqualTo(51L);
        assertThat(action.getLibraryItemId()).isEqualTo(11L);
        assertThat(action.getPhotoAssetId()).isEqualTo(701L);
        assertThat(action.getActionType())
                .isEqualTo(DiaryCoverLibraryModerationActionType.RESTORE_PHOTO);
        assertThat(action.getPreviousStatus()).isEqualTo("BLOCKED");
        assertThat(action.getResultingStatus()).isEqualTo("ACTIVE");
        verifyNoInteractions(reportMapper);
    }

    @Test
    void alreadyRestoredItemCannotBeRestoredAgain() {
        adminIsSignedIn();
        DiaryCoverLibraryItem item = blockedItem();
        item.setStatus(DiaryCoverLibraryItemStatus.PUBLISHED);
        when(itemMapper.findByIdForUpdate(11L)).thenReturn(item);

        assertThatThrownBy(() -> service.restoreItem(
                1L, 11L, restoreForm("재복구", null)))
                .isInstanceOf(DiaryCoverLibraryModerationException.class);

        verify(actionMapper, never()).findLatestBlockItemByLibraryItemId(anyLong());
        verify(actionMapper, never()).insert(any());
    }

    @Test
    void alreadyRestoredPhotoCannotBeRestoredAgain() {
        adminIsSignedIn();
        DiaryCoverLibraryPhotoAsset asset = blockedAsset();
        asset.setStatus(DiaryCoverLibraryPhotoAssetStatus.ACTIVE);
        when(photoAssetMapper.findByIdForUpdate(701L)).thenReturn(asset);

        assertThatThrownBy(() -> service.restorePhoto(
                1L, 701L, restoreForm("재복구", null)))
                .isInstanceOf(DiaryCoverLibraryModerationException.class);

        verify(actionMapper, never()).findLatestBlockPhotoByPhotoAssetId(anyLong());
        verify(actionMapper, never()).insert(any());
    }

    @Test
    void itemWithoutAValidBlockActionCannotBeRestored() {
        adminIsSignedIn();
        when(itemMapper.findByIdForUpdate(11L)).thenReturn(blockedItem());
        when(actionMapper.findLatestBlockItemByLibraryItemId(11L)).thenReturn(null);

        assertThatThrownBy(() -> service.restoreItem(
                1L, 11L, restoreForm("복구", null)))
                .isInstanceOf(DiaryCoverLibraryModerationException.class);

        verify(itemMapper, never()).restoreByAdmin(anyLong(), any());
    }

    @Test
    void deletedPreviousStatusCannotBeUsedForItemRestore() {
        adminIsSignedIn();
        when(itemMapper.findByIdForUpdate(11L)).thenReturn(blockedItem());
        when(actionMapper.findLatestBlockItemByLibraryItemId(11L))
                .thenReturn(blockItemAction("DELETED"));

        assertThatThrownBy(() -> service.restoreItem(
                1L, 11L, restoreForm("복구", null)))
                .isInstanceOf(DiaryCoverLibraryModerationException.class);

        verify(itemMapper, never()).restoreByAdmin(anyLong(), any());
    }

    @Test
    void photoWithoutABlockActionCannotBeRestored() {
        adminIsSignedIn();
        when(photoAssetMapper.findByIdForUpdate(701L)).thenReturn(blockedAsset());
        when(actionMapper.findLatestBlockPhotoByPhotoAssetId(701L)).thenReturn(null);

        assertThatThrownBy(() -> service.restorePhoto(
                1L, 701L, restoreForm("복구", null)))
                .isInstanceOf(DiaryCoverLibraryModerationException.class);

        verify(photoAssetMapper, never()).restoreByAdmin(anyLong());
    }

    @Test
    void restoreRequiresAnAdminAndANonBlankReason() {
        User member = new User();
        member.setId(2L);
        member.setUserRole(UserRole.USER);
        when(userMapper.findById(2L)).thenReturn(member);

        assertThatThrownBy(() -> service.restoreItem(
                2L, 11L, restoreForm("사유", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));

        adminIsSignedIn();
        assertThatThrownBy(() -> service.restoreItem(
                1L, 11L, restoreForm("  ", null)))
                .isInstanceOf(DiaryCoverLibraryModerationException.class);
    }

    @Test
    void actionFailureLeavesRestoreInsideTheTransactionalBoundary() throws Exception {
        adminIsSignedIn();
        when(itemMapper.findByIdForUpdate(11L)).thenReturn(blockedItem());
        when(actionMapper.findLatestBlockItemByLibraryItemId(11L))
                .thenReturn(blockItemAction("PUBLISHED"));
        when(itemMapper.restoreByAdmin(11L, DiaryCoverLibraryItemStatus.PUBLISHED))
                .thenReturn(1);
        when(actionMapper.insert(any())).thenThrow(new IllegalStateException("insert failed"));

        assertThatThrownBy(() -> service.restoreItem(
                1L, 11L, restoreForm("복구", null)))
                .isInstanceOf(IllegalStateException.class);

        Transactional itemBoundary = DiaryCoverLibraryModerationServiceImpl.class
                .getMethod("restoreItem", Long.class, Long.class,
                        DiaryCoverLibraryRestoreForm.class)
                .getAnnotation(Transactional.class);
        Transactional photoBoundary = DiaryCoverLibraryModerationServiceImpl.class
                .getMethod("restorePhoto", Long.class, Long.class,
                        DiaryCoverLibraryRestoreForm.class)
                .getAnnotation(Transactional.class);
        assertThat(itemBoundary).isNotNull();
        assertThat(photoBoundary).isNotNull();
    }

    private DiaryCoverLibraryModerationAction capturedAction() {
        ArgumentCaptor<DiaryCoverLibraryModerationAction> captor =
                ArgumentCaptor.forClass(DiaryCoverLibraryModerationAction.class);
        verify(actionMapper).insert(captor.capture());
        return captor.getValue();
    }

    private void adminIsSignedIn() {
        User admin = new User();
        admin.setId(1L);
        admin.setUserRole(UserRole.ADMIN);
        when(userMapper.findById(1L)).thenReturn(admin);
    }

    private DiaryCoverLibraryItem blockedItem() {
        DiaryCoverLibraryItem item = new DiaryCoverLibraryItem();
        item.setId(11L);
        item.setStatus(DiaryCoverLibraryItemStatus.BLOCKED);
        item.setBlockedAt(Timestamp.from(NOW.minusSeconds(3600)));
        item.setBlockedReason("기존 차단");
        return item;
    }

    private DiaryCoverLibraryPhotoAsset blockedAsset() {
        DiaryCoverLibraryPhotoAsset asset = new DiaryCoverLibraryPhotoAsset();
        asset.setId(701L);
        asset.setLibraryItemId(11L);
        asset.setStatus(DiaryCoverLibraryPhotoAssetStatus.BLOCKED);
        asset.setBlockedAt(Timestamp.from(NOW.minusSeconds(3600)));
        asset.setBlockedReason("기존 차단");
        return asset;
    }

    private DiaryCoverLibraryModerationAction blockItemAction(String previousStatus) {
        DiaryCoverLibraryModerationAction action = new DiaryCoverLibraryModerationAction();
        action.setId(80L);
        action.setReportId(50L);
        action.setLibraryItemId(11L);
        action.setActionType(DiaryCoverLibraryModerationActionType.BLOCK_ITEM);
        action.setPreviousStatus(previousStatus);
        action.setResultingStatus("BLOCKED");
        return action;
    }

    private DiaryCoverLibraryModerationAction blockPhotoAction() {
        DiaryCoverLibraryModerationAction action = new DiaryCoverLibraryModerationAction();
        action.setId(81L);
        action.setReportId(51L);
        action.setLibraryItemId(11L);
        action.setPhotoAssetId(701L);
        action.setActionType(DiaryCoverLibraryModerationActionType.BLOCK_PHOTO);
        action.setPreviousStatus("ACTIVE");
        action.setResultingStatus("BLOCKED");
        return action;
    }

    private DiaryCoverLibraryRestoreForm restoreForm(String reason, String adminNote) {
        DiaryCoverLibraryRestoreForm form = new DiaryCoverLibraryRestoreForm();
        form.setReason(reason);
        form.setAdminNote(adminNote);
        return form;
    }
}
