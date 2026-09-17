package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryReportForm;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import com.example.travlediary.model.DiaryCoverLibraryReport;
import com.example.travlediary.model.DiaryCoverLibraryReportReason;
import com.example.travlediary.model.DiaryCoverLibraryReportStatus;
import com.example.travlediary.repository.diary.DiaryCoverLibraryItemMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryPhotoAssetMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DiaryCoverLibraryReportServiceImpl implements DiaryCoverLibraryReportService {

    private static final int DESCRIPTION_MAX_LENGTH = 1000;

    private final DiaryCoverLibraryItemMapper itemMapper;
    private final DiaryCoverLibraryPhotoAssetMapper photoAssetMapper;
    private final DiaryCoverLibraryReportMapper reportMapper;

    @Override
    @Transactional
    public void submitReport(Long reporterUserId,
                             Long libraryItemId,
                             DiaryCoverLibraryReportForm form) {
        DiaryCoverValues.requireUser(reporterUserId);
        if (libraryItemId == null || form == null) {
            throw invalidRequest("신고 정보를 확인해 주세요.");
        }

        DiaryCoverLibraryReportReason reason = parseReason(form.getReasonCode());
        String description = normalizeDescription(form.getDescription(), reason);
        DiaryCoverLibraryItem item = itemMapper.findPublishedByIdForUpdate(libraryItemId);
        if (item == null || item.getSnapshotVersion() == null) {
            throw new DiaryCoverLibraryReportException(
                    DiaryCoverLibraryReportException.Reason.UNAVAILABLE,
                    "공개 중인 표지만 신고할 수 있습니다.");
        }

        Long photoAssetId = form.getPhotoAssetId();
        if (photoAssetId != null) {
            DiaryCoverLibraryPhotoAsset asset = photoAssetMapper.findById(photoAssetId);
            if (asset == null
                    || !item.getId().equals(asset.getLibraryItemId())
                    || !item.getSnapshotVersion().equals(asset.getSnapshotVersion())) {
                throw new DiaryCoverLibraryReportException(
                        DiaryCoverLibraryReportException.Reason.INVALID_PHOTO,
                        "신고할 공유 사진을 찾을 수 없습니다.");
            }
        }

        DiaryCoverLibraryReport report = new DiaryCoverLibraryReport();
        report.setLibraryItemId(item.getId());
        report.setReportedSnapshotVersion(item.getSnapshotVersion());
        report.setPhotoAssetId(photoAssetId);
        report.setReporterUserId(reporterUserId);
        report.setReasonCode(reason);
        report.setDescription(description);
        report.setStatus(DiaryCoverLibraryReportStatus.PENDING);

        try {
            if (reportMapper.insert(report) != 1) {
                throw new IllegalStateException("신고를 저장하지 못했습니다.");
            }
        } catch (DuplicateKeyException exception) {
            throw new DiaryCoverLibraryReportException(
                    DiaryCoverLibraryReportException.Reason.DUPLICATE,
                    "이미 신고한 항목입니다.");
        }
    }

    private DiaryCoverLibraryReportReason parseReason(String value) {
        try {
            return DiaryCoverLibraryReportReason.valueOf(
                    value == null ? "" : value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidRequest("신고 사유를 선택해 주세요.");
        }
    }

    private String normalizeDescription(String value,
                                        DiaryCoverLibraryReportReason reason) {
        String description = value == null ? "" : value.strip();
        if (description.length() > DESCRIPTION_MAX_LENGTH) {
            throw invalidRequest("상세 설명은 1000자 이하로 입력해 주세요.");
        }
        if (reason == DiaryCoverLibraryReportReason.OTHER && description.isEmpty()) {
            throw invalidRequest("기타 사유의 상세 설명을 입력해 주세요.");
        }
        return description.isEmpty() ? null : description;
    }

    private DiaryCoverLibraryReportException invalidRequest(String message) {
        return new DiaryCoverLibraryReportException(
                DiaryCoverLibraryReportException.Reason.INVALID_REQUEST, message);
    }
}
