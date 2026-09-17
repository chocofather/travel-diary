package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryReportForm;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryItemStatus;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import com.example.travlediary.model.DiaryCoverLibraryReport;
import com.example.travlediary.model.DiaryCoverLibraryReportReason;
import com.example.travlediary.model.DiaryCoverLibraryReportStatus;
import com.example.travlediary.repository.diary.DiaryCoverLibraryItemMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryPhotoAssetMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryCoverLibraryReportServiceTest {

    @Mock private DiaryCoverLibraryItemMapper itemMapper;
    @Mock private DiaryCoverLibraryPhotoAssetMapper photoAssetMapper;
    @Mock private DiaryCoverLibraryReportMapper reportMapper;

    private DiaryCoverLibraryReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DiaryCoverLibraryReportServiceImpl(
                itemMapper, photoAssetMapper, reportMapper);
    }

    @Test
    void itemReportUsesTheServerVerifiedCurrentSnapshot() {
        when(itemMapper.findPublishedByIdForUpdate(11L)).thenReturn(item(11L, 3));
        when(reportMapper.insert(any())).thenReturn(1);

        service.submitReport(7L, 11L,
                form(null, DiaryCoverLibraryReportReason.COPYRIGHT, null));

        ArgumentCaptor<DiaryCoverLibraryReport> captor =
                ArgumentCaptor.forClass(DiaryCoverLibraryReport.class);
        verify(reportMapper).insert(captor.capture());
        DiaryCoverLibraryReport report = captor.getValue();
        assertThat(report.getLibraryItemId()).isEqualTo(11L);
        assertThat(report.getReportedSnapshotVersion()).isEqualTo(3);
        assertThat(report.getPhotoAssetId()).isNull();
        assertThat(report.getReporterUserId()).isEqualTo(7L);
        assertThat(report.getReasonCode()).isEqualTo(DiaryCoverLibraryReportReason.COPYRIGHT);
        assertThat(report.getStatus()).isEqualTo(DiaryCoverLibraryReportStatus.PENDING);
    }

    @Test
    void includedPhotoReportRequiresMatchingItemAndSnapshot() {
        when(itemMapper.findPublishedByIdForUpdate(11L)).thenReturn(item(11L, 3));
        when(photoAssetMapper.findById(701L)).thenReturn(asset(701L, 11L, 3));
        when(reportMapper.insert(any())).thenReturn(1);

        service.submitReport(7L, 11L,
                form(701L, DiaryCoverLibraryReportReason.PORTRAIT_PRIVACY, "설명"));

        ArgumentCaptor<DiaryCoverLibraryReport> captor =
                ArgumentCaptor.forClass(DiaryCoverLibraryReport.class);
        verify(reportMapper).insert(captor.capture());
        assertThat(captor.getValue().getPhotoAssetId()).isEqualTo(701L);
        assertThat(captor.getValue().getReportedSnapshotVersion()).isEqualTo(3);
    }

    @Test
    void excludedPhotoCannotBeReportedAsAPhotoTarget() {
        when(itemMapper.findPublishedByIdForUpdate(11L)).thenReturn(item(11L, 3));
        when(photoAssetMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.submitReport(7L, 11L,
                form(999L, DiaryCoverLibraryReportReason.INAPPROPRIATE, null)))
                .isInstanceOfSatisfying(DiaryCoverLibraryReportException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(DiaryCoverLibraryReportException.Reason.INVALID_PHOTO));

        verify(reportMapper, never()).insert(any());
    }

    @Test
    void photoFromAnotherItemIsRejected() {
        when(itemMapper.findPublishedByIdForUpdate(11L)).thenReturn(item(11L, 3));
        when(photoAssetMapper.findById(701L)).thenReturn(asset(701L, 12L, 3));

        assertInvalidPhoto(form(701L, DiaryCoverLibraryReportReason.COPYRIGHT, null));
    }

    @Test
    void photoFromAnotherSnapshotIsRejected() {
        when(itemMapper.findPublishedByIdForUpdate(11L)).thenReturn(item(11L, 4));
        when(photoAssetMapper.findById(701L)).thenReturn(asset(701L, 11L, 3));

        assertInvalidPhoto(form(701L, DiaryCoverLibraryReportReason.COPYRIGHT, null));
    }

    @Test
    void unpublishedItemCannotReceiveANewReport() {
        when(itemMapper.findPublishedByIdForUpdate(11L)).thenReturn(null);

        assertThatThrownBy(() -> service.submitReport(7L, 11L,
                form(null, DiaryCoverLibraryReportReason.SPAM, null)))
                .isInstanceOfSatisfying(DiaryCoverLibraryReportException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(DiaryCoverLibraryReportException.Reason.UNAVAILABLE));

        verify(reportMapper, never()).insert(any());
    }

    @Test
    void otherReasonRequiresADescription() {
        assertThatThrownBy(() -> service.submitReport(7L, 11L,
                form(null, DiaryCoverLibraryReportReason.OTHER, "  ")))
                .isInstanceOfSatisfying(DiaryCoverLibraryReportException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(DiaryCoverLibraryReportException.Reason.INVALID_REQUEST));

        verify(reportMapper, never()).insert(any());
    }

    @Test
    void duplicateUniqueTargetHasASafeDomainError() {
        when(itemMapper.findPublishedByIdForUpdate(11L)).thenReturn(item(11L, 3));
        when(reportMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("uq_dclr_reporter_target"));

        assertThatThrownBy(() -> service.submitReport(7L, 11L,
                form(null, DiaryCoverLibraryReportReason.SPAM, null)))
                .isInstanceOfSatisfying(DiaryCoverLibraryReportException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(DiaryCoverLibraryReportException.Reason.DUPLICATE));
    }

    @Test
    void theSameMemberCanReportANewSnapshotAgain() {
        DiaryCoverLibraryItem item = item(11L, 3);
        when(itemMapper.findPublishedByIdForUpdate(11L)).thenReturn(item);
        when(reportMapper.insert(any())).thenReturn(1);

        service.submitReport(7L, 11L,
                form(null, DiaryCoverLibraryReportReason.SPAM, null));
        item.setSnapshotVersion(4);
        service.submitReport(7L, 11L,
                form(null, DiaryCoverLibraryReportReason.SPAM, null));

        ArgumentCaptor<DiaryCoverLibraryReport> captor =
                ArgumentCaptor.forClass(DiaryCoverLibraryReport.class);
        verify(reportMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DiaryCoverLibraryReport::getReportedSnapshotVersion)
                .containsExactly(3, 4);
    }

    private void assertInvalidPhoto(DiaryCoverLibraryReportForm form) {
        assertThatThrownBy(() -> service.submitReport(7L, 11L, form))
                .isInstanceOfSatisfying(DiaryCoverLibraryReportException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(DiaryCoverLibraryReportException.Reason.INVALID_PHOTO));
        verify(reportMapper, never()).insert(any());
    }

    private DiaryCoverLibraryReportForm form(Long photoAssetId,
                                              DiaryCoverLibraryReportReason reason,
                                              String description) {
        DiaryCoverLibraryReportForm form = new DiaryCoverLibraryReportForm();
        form.setPhotoAssetId(photoAssetId);
        form.setReasonCode(reason == null ? null : reason.name());
        form.setDescription(description);
        return form;
    }

    private DiaryCoverLibraryItem item(Long id, int snapshotVersion) {
        DiaryCoverLibraryItem item = new DiaryCoverLibraryItem();
        item.setId(id);
        item.setStatus(DiaryCoverLibraryItemStatus.PUBLISHED);
        item.setSnapshotVersion(snapshotVersion);
        return item;
    }

    private DiaryCoverLibraryPhotoAsset asset(Long id, Long itemId, int snapshotVersion) {
        DiaryCoverLibraryPhotoAsset asset = new DiaryCoverLibraryPhotoAsset();
        asset.setId(id);
        asset.setLibraryItemId(itemId);
        asset.setSnapshotVersion(snapshotVersion);
        return asset;
    }
}
