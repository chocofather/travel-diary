package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryModerationForm;
import com.example.travlediary.dto.DiaryCoverLibraryReportDetailDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportListItemDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportPageDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportStatusFilter;
import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryItemStatus;
import com.example.travlediary.model.DiaryCoverLibraryModerationAction;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAssetStatus;
import com.example.travlediary.model.DiaryCoverLibraryReport;
import com.example.travlediary.model.DiaryCoverLibraryReportStatus;
import com.example.travlediary.model.DiaryCoverLibraryResolutionAction;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryCoverLibraryModerationServiceTest {

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
    void rejectingClosesTheReportWithoutChangingContent() {
        adminIsSignedIn();
        stubPending(itemReport());
        when(reportMapper.markProcessed(eq(50L), eq(DiaryCoverLibraryReportStatus.REJECTED),
                eq(DiaryCoverLibraryResolutionAction.NO_ACTION), eq(1L), any(),
                eq("기각 메모"))).thenReturn(1);

        service.process(1L, 50L,
                form("REJECT", null, null, "기각 메모"));

        verify(reportMapper).markProcessed(eq(50L),
                eq(DiaryCoverLibraryReportStatus.REJECTED),
                eq(DiaryCoverLibraryResolutionAction.NO_ACTION), eq(1L), any(),
                eq("기각 메모"));
        verify(itemMapper, never()).blockByAdmin(anyLong(), any(), any(), any());
        verify(photoAssetMapper, never()).blockByAdmin(anyLong(), any(), any());
        verify(actionMapper, never()).insert(any());
    }

    @Test
    void itemBlockingChangesTheItemAndRecordsAnAction() {
        adminIsSignedIn();
        DiaryCoverLibraryReport report = itemReport();
        stubPending(report);
        when(itemMapper.blockByAdmin(eq(11L), eq(DiaryCoverLibraryItemStatus.PUBLISHED),
                any(), eq("저작권 침해 확인"))).thenReturn(1);
        when(actionMapper.insert(any())).thenReturn(1);
        when(reportMapper.markProcessed(eq(50L), eq(DiaryCoverLibraryReportStatus.RESOLVED),
                eq(DiaryCoverLibraryResolutionAction.ITEM_BLOCKED), eq(1L), any(),
                eq("내부 메모"))).thenReturn(1);

        service.process(1L, 50L,
                form("BLOCK_ITEM", null, "저작권 침해 확인", "내부 메모"));

        verify(itemMapper).blockByAdmin(eq(11L),
                eq(DiaryCoverLibraryItemStatus.PUBLISHED), any(), eq("저작권 침해 확인"));
        ArgumentCaptor<DiaryCoverLibraryModerationAction> captor =
                ArgumentCaptor.forClass(DiaryCoverLibraryModerationAction.class);
        verify(actionMapper).insert(captor.capture());
        assertThat(captor.getValue().getActionType().name()).isEqualTo("BLOCK_ITEM");
        assertThat(captor.getValue().getPreviousStatus()).isEqualTo("PUBLISHED");
        assertThat(captor.getValue().getResultingStatus()).isEqualTo("BLOCKED");
        assertThat(captor.getValue().getReason()).isEqualTo("저작권 침해 확인");
    }

    @Test
    void photoBlockingUsesOnlyThePhotoTargetStoredOnTheReport() {
        adminIsSignedIn();
        DiaryCoverLibraryReport report = photoReport();
        stubPending(report);
        DiaryCoverLibraryPhotoAsset asset = asset(701L, 11L, 3);
        when(photoAssetMapper.findByIdForUpdate(701L)).thenReturn(asset);
        when(photoAssetMapper.blockByAdmin(eq(701L), any(), eq("초상권 침해 확인")))
                .thenReturn(1);
        when(actionMapper.insert(any())).thenReturn(1);
        when(reportMapper.markProcessed(eq(50L), eq(DiaryCoverLibraryReportStatus.RESOLVED),
                eq(DiaryCoverLibraryResolutionAction.PHOTO_BLOCKED), eq(1L), any(),
                eq(null))).thenReturn(1);

        service.process(1L, 50L,
                form("BLOCK_PHOTO", 701L, "초상권 침해 확인", null));

        verify(photoAssetMapper).blockByAdmin(eq(701L), any(), eq("초상권 침해 확인"));
        verify(itemMapper, never()).blockByAdmin(anyLong(), any(), any(), any());
        verify(reportMapper).markProcessed(eq(50L),
                eq(DiaryCoverLibraryReportStatus.RESOLVED),
                eq(DiaryCoverLibraryResolutionAction.PHOTO_BLOCKED), eq(1L), any(),
                eq(null));
    }

    @Test
    void combinedBlockingRecordsTwoActionsAndOneResolution() {
        adminIsSignedIn();
        DiaryCoverLibraryReport report = photoReport();
        stubPending(report);
        when(photoAssetMapper.findByIdForUpdate(701L))
                .thenReturn(asset(701L, 11L, 3));
        when(itemMapper.blockByAdmin(eq(11L), eq(DiaryCoverLibraryItemStatus.PUBLISHED),
                any(), eq("복합 침해"))).thenReturn(1);
        when(photoAssetMapper.blockByAdmin(eq(701L), any(), eq("복합 침해"))).thenReturn(1);
        when(actionMapper.insert(any())).thenReturn(1);
        when(reportMapper.markProcessed(eq(50L), eq(DiaryCoverLibraryReportStatus.RESOLVED),
                eq(DiaryCoverLibraryResolutionAction.ITEM_AND_PHOTO_BLOCKED),
                eq(1L), any(), eq(null))).thenReturn(1);

        service.process(1L, 50L,
                form("BLOCK_ITEM_AND_PHOTO", 701L, "복합 침해", null));

        verify(actionMapper, org.mockito.Mockito.times(2)).insert(any());
        verify(reportMapper).markProcessed(eq(50L),
                eq(DiaryCoverLibraryReportStatus.RESOLVED),
                eq(DiaryCoverLibraryResolutionAction.ITEM_AND_PHOTO_BLOCKED),
                eq(1L), any(), eq(null));
    }

    @Test
    void processedReportCannotBeHandledAgain() {
        adminIsSignedIn();
        DiaryCoverLibraryReport report = itemReport();
        report.setStatus(DiaryCoverLibraryReportStatus.RESOLVED);
        when(reportMapper.findByIdForUpdate(50L)).thenReturn(report);

        assertThatThrownBy(() -> service.process(1L, 50L,
                form("REJECT", null, null, null)))
                .isInstanceOf(DiaryCoverLibraryModerationException.class)
                .hasMessageContaining("이미 처리");

        verify(itemMapper, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void itemReportCannotBeUsedToBlockAPhoto() {
        adminIsSignedIn();
        stubPending(itemReport());

        assertThatThrownBy(() -> service.process(1L, 50L,
                form("BLOCK_PHOTO", 701L, "사유", null)))
                .isInstanceOf(DiaryCoverLibraryModerationException.class);

        verify(photoAssetMapper, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void manipulatedPhotoIdIsRejectedBeforeContentChanges() {
        adminIsSignedIn();
        stubPending(photoReport());

        assertThatThrownBy(() -> service.process(1L, 50L,
                form("BLOCK_PHOTO", 702L, "사유", null)))
                .isInstanceOf(DiaryCoverLibraryModerationException.class);

        verify(photoAssetMapper, never()).findByIdForUpdate(anyLong());
        verify(itemMapper, never()).blockByAdmin(anyLong(), any(), any(), any());
    }

    @Test
    void actionInsertFailurePreventsTheReportFromBeingMarkedProcessed() {
        adminIsSignedIn();
        stubPending(itemReport());
        when(itemMapper.blockByAdmin(eq(11L), eq(DiaryCoverLibraryItemStatus.PUBLISHED),
                any(), eq("사유"))).thenReturn(1);
        when(actionMapper.insert(any())).thenThrow(new IllegalStateException("insert failed"));

        assertThatThrownBy(() -> service.process(1L, 50L,
                form("BLOCK_ITEM", null, "사유", null)))
                .isInstanceOf(IllegalStateException.class);

        verify(reportMapper, never()).markProcessed(anyLong(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void processingBoundaryIsTransactional() throws Exception {
        Transactional transactional = DiaryCoverLibraryModerationServiceImpl.class
                .getMethod("process", Long.class, Long.class,
                        DiaryCoverLibraryModerationForm.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    void nonAdminCannotProcessAReport() {
        User member = new User();
        member.setId(2L);
        member.setUserRole(UserRole.USER);
        when(userMapper.findById(2L)).thenReturn(member);

        assertThatThrownBy(() -> service.process(2L, 50L,
                form("REJECT", null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void pendingReportPageUsesTheStatusIndexFriendlyFilter() {
        adminIsSignedIn();
        DiaryCoverLibraryReportListItemDto row = new DiaryCoverLibraryReportListItemDto();
        row.setReportId(50L);
        when(reportMapper.countForAdmin(DiaryCoverLibraryReportStatus.PENDING)).thenReturn(1);
        when(reportMapper.findForAdmin(DiaryCoverLibraryReportStatus.PENDING, 0, 20))
                .thenReturn(List.of(row));

        DiaryCoverLibraryReportPageDto page = service.getReports(
                1L, DiaryCoverLibraryReportStatusFilter.PENDING, 1);

        assertThat(page.items()).containsExactly(row);
        assertThat(page.filter()).isEqualTo(DiaryCoverLibraryReportStatusFilter.PENDING);
        verify(reportMapper).findForAdmin(DiaryCoverLibraryReportStatus.PENDING, 0, 20);
    }

    @Test
    void reportDetailUsesTheReportedSnapshotEvenAfterTheItemChanges() {
        adminIsSignedIn();
        DiaryCoverLibraryReport report = photoReport();
        report.setStatus(DiaryCoverLibraryReportStatus.PENDING);
        DiaryCoverLibraryItem item = item();
        item.setStatus(DiaryCoverLibraryItemStatus.WITHDRAWN);
        item.setSnapshotVersion(4);
        DiaryCoverLibraryElement photo = new DiaryCoverLibraryElement();
        photo.setElementType("PHOTO");
        photo.setPhotoAssetId(701L);
        when(reportMapper.findById(50L)).thenReturn(report);
        when(itemMapper.findById(11L)).thenReturn(item);
        when(elementMapper.findAllByLibraryItemIdAndSnapshotVersion(11L, 3))
                .thenReturn(List.of(photo));
        DiaryCoverLibraryPhotoAsset asset = asset(701L, 11L, 3);
        when(photoAssetMapper.findAllByLibraryItemIdAndSnapshotVersion(11L, 3))
                .thenReturn(List.of(asset));
        User reporter = new User();
        reporter.setNickname("신고자");
        report.setReporterUserId(7L);
        when(userMapper.findById(7L)).thenReturn(reporter);

        DiaryCoverLibraryReportDetailDto detail = service.getReportDetail(1L, 50L);

        assertThat(detail.report()).isSameAs(report);
        assertThat(detail.item()).isSameAs(item);
        assertThat(detail.elements()).containsExactly(photo);
        assertThat(detail.reporterDisplayName()).isEqualTo("신고자");
        assertThat(detail.targetPhotoAsset()).isSameAs(asset);
        assertThat(photo.getImageUrl()).isEqualTo("/diaries/cover-library/assets/701");
        verify(elementMapper).findAllByLibraryItemIdAndSnapshotVersion(11L, 3);
    }

    private void stubPending(DiaryCoverLibraryReport report) {
        when(reportMapper.findByIdForUpdate(50L)).thenReturn(report);
        when(itemMapper.findByIdForUpdate(11L)).thenReturn(item());
    }

    private void adminIsSignedIn() {
        User admin = new User();
        admin.setId(1L);
        admin.setUserRole(UserRole.ADMIN);
        when(userMapper.findById(1L)).thenReturn(admin);
    }

    private DiaryCoverLibraryReport itemReport() {
        DiaryCoverLibraryReport report = new DiaryCoverLibraryReport();
        report.setId(50L);
        report.setLibraryItemId(11L);
        report.setReportedSnapshotVersion(3);
        report.setStatus(DiaryCoverLibraryReportStatus.PENDING);
        return report;
    }

    private DiaryCoverLibraryReport photoReport() {
        DiaryCoverLibraryReport report = itemReport();
        report.setPhotoAssetId(701L);
        return report;
    }

    private DiaryCoverLibraryItem item() {
        DiaryCoverLibraryItem item = new DiaryCoverLibraryItem();
        item.setId(11L);
        item.setStatus(DiaryCoverLibraryItemStatus.PUBLISHED);
        item.setSnapshotVersion(3);
        return item;
    }

    private DiaryCoverLibraryPhotoAsset asset(Long id, Long itemId, int snapshotVersion) {
        DiaryCoverLibraryPhotoAsset asset = new DiaryCoverLibraryPhotoAsset();
        asset.setId(id);
        asset.setLibraryItemId(itemId);
        asset.setSnapshotVersion(snapshotVersion);
        asset.setStatus(DiaryCoverLibraryPhotoAssetStatus.ACTIVE);
        return asset;
    }

    private DiaryCoverLibraryModerationForm form(String decision,
                                                  Long photoAssetId,
                                                  String reason,
                                                  String adminNote) {
        DiaryCoverLibraryModerationForm form = new DiaryCoverLibraryModerationForm();
        form.setDecision(decision);
        form.setPhotoAssetId(photoAssetId);
        form.setReason(reason);
        form.setAdminNote(adminNote);
        return form;
    }
}
