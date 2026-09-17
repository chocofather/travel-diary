package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryModerationForm;
import com.example.travlediary.dto.DiaryCoverLibraryReportDetailDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportListItemDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportPageDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportStatusFilter;
import com.example.travlediary.dto.DiaryCoverLibraryRestoreForm;
import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryItemStatus;
import com.example.travlediary.model.DiaryCoverLibraryModerationAction;
import com.example.travlediary.model.DiaryCoverLibraryModerationActionType;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DiaryCoverLibraryModerationServiceImpl
        implements DiaryCoverLibraryModerationService {

    private static final int REASON_MAX_LENGTH = 500;
    private static final int PAGE_SIZE = 20;
    private static final String ASSET_URL_PREFIX = "/diaries/cover-library/assets/";

    private final DiaryCoverLibraryReportMapper reportMapper;
    private final DiaryCoverLibraryModerationActionMapper actionMapper;
    private final DiaryCoverLibraryItemMapper itemMapper;
    private final DiaryCoverLibraryPhotoAssetMapper photoAssetMapper;
    private final DiaryCoverLibraryElementMapper elementMapper;
    private final UserMapper userMapper;
    private final Clock clock;

    @Autowired
    public DiaryCoverLibraryModerationServiceImpl(
            DiaryCoverLibraryReportMapper reportMapper,
            DiaryCoverLibraryModerationActionMapper actionMapper,
            DiaryCoverLibraryItemMapper itemMapper,
            DiaryCoverLibraryPhotoAssetMapper photoAssetMapper,
            DiaryCoverLibraryElementMapper elementMapper,
            UserMapper userMapper) {
        this(reportMapper, actionMapper, itemMapper, photoAssetMapper,
                elementMapper, userMapper, Clock.systemUTC());
    }

    DiaryCoverLibraryModerationServiceImpl(
            DiaryCoverLibraryReportMapper reportMapper,
            DiaryCoverLibraryModerationActionMapper actionMapper,
            DiaryCoverLibraryItemMapper itemMapper,
            DiaryCoverLibraryPhotoAssetMapper photoAssetMapper,
            DiaryCoverLibraryElementMapper elementMapper,
            UserMapper userMapper,
            Clock clock) {
        this.reportMapper = reportMapper;
        this.actionMapper = actionMapper;
        this.itemMapper = itemMapper;
        this.photoAssetMapper = photoAssetMapper;
        this.elementMapper = elementMapper;
        this.userMapper = userMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public DiaryCoverLibraryReportPageDto getReports(
            Long adminUserId,
            DiaryCoverLibraryReportStatusFilter filter,
            int page) {
        requireAdmin(adminUserId);
        DiaryCoverLibraryReportStatusFilter normalized = filter == null
                ? DiaryCoverLibraryReportStatusFilter.PENDING : filter;
        DiaryCoverLibraryReportStatus status = normalized.getStatus();
        int totalCount = reportMapper.countForAdmin(status);
        int totalPages = Math.max(1, (totalCount + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.min(Math.max(1, page), totalPages);
        List<DiaryCoverLibraryReportListItemDto> reports = totalCount == 0
                ? List.of()
                : reportMapper.findForAdmin(
                        status, (currentPage - 1) * PAGE_SIZE, PAGE_SIZE);
        return new DiaryCoverLibraryReportPageDto(
                List.copyOf(reports), normalized, currentPage,
                totalPages, totalCount, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public DiaryCoverLibraryReportDetailDto getReportDetail(
            Long adminUserId, Long reportId) {
        requireAdmin(adminUserId);
        DiaryCoverLibraryReport report = reportMapper.findById(reportId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다.");
        }
        DiaryCoverLibraryItem item = itemMapper.findById(report.getLibraryItemId());
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "신고 대상 표지를 찾을 수 없습니다.");
        }

        List<DiaryCoverLibraryElement> elements =
                elementMapper.findAllByLibraryItemIdAndSnapshotVersion(
                        report.getLibraryItemId(), report.getReportedSnapshotVersion());
        List<DiaryCoverLibraryPhotoAsset> assets =
                photoAssetMapper.findAllByLibraryItemIdAndSnapshotVersion(
                        report.getLibraryItemId(), report.getReportedSnapshotVersion());
        Map<Long, DiaryCoverLibraryPhotoAsset> assetsById = assets.stream()
                .collect(Collectors.toMap(
                        DiaryCoverLibraryPhotoAsset::getId, Function.identity()));
        for (DiaryCoverLibraryElement element : elements) {
            DiaryCoverLibraryPhotoAsset asset = assetsById.get(element.getPhotoAssetId());
            if (asset != null && asset.getStatus() == DiaryCoverLibraryPhotoAssetStatus.ACTIVE) {
                element.setImageUrl(ASSET_URL_PREFIX + asset.getId());
            } else if ("PHOTO".equals(element.getElementType())) {
                element.setImageUrl(null);
            }
        }

        String reporterDisplayName = "탈퇴한 사용자";
        if (report.getReporterUserId() != null) {
            User reporter = userMapper.findById(report.getReporterUserId());
            if (reporter != null && reporter.getNickname() != null
                    && !reporter.getNickname().isBlank()) {
                reporterDisplayName = reporter.getNickname();
            }
        }
        return new DiaryCoverLibraryReportDetailDto(
                report, item, List.copyOf(elements),
                assetsById.get(report.getPhotoAssetId()), reporterDisplayName);
    }

    @Override
    @Transactional
    public void process(Long adminUserId,
                        Long reportId,
                        DiaryCoverLibraryModerationForm form) {
        requireAdmin(adminUserId);
        Decision decision = parseDecision(form == null ? null : form.getDecision());
        String adminNote = normalizeOptional(form == null ? null : form.getAdminNote());

        DiaryCoverLibraryReport report = reportMapper.findByIdForUpdate(reportId);
        if (report == null) {
            throw new DiaryCoverLibraryModerationException("신고를 찾을 수 없습니다.");
        }
        if (report.getStatus() != DiaryCoverLibraryReportStatus.PENDING) {
            throw new DiaryCoverLibraryModerationException("이미 처리된 신고입니다.");
        }

        DiaryCoverLibraryItem item = itemMapper.findByIdForUpdate(report.getLibraryItemId());
        if (item == null) {
            throw new DiaryCoverLibraryModerationException("신고 대상 표지를 찾을 수 없습니다.");
        }

        Timestamp processedAt = Timestamp.from(clock.instant());
        if (decision == Decision.REJECT) {
            markProcessed(report, DiaryCoverLibraryReportStatus.REJECTED,
                    DiaryCoverLibraryResolutionAction.NO_ACTION,
                    adminUserId, processedAt, adminNote);
            return;
        }

        String reason = normalizeReason(form.getReason());
        DiaryCoverLibraryPhotoAsset asset = null;
        if (decision.blocksPhoto()) {
            asset = lockTargetPhoto(report, form.getPhotoAssetId());
        }

        if (decision.blocksItem()) {
            blockItem(report, item, adminUserId, processedAt, reason, adminNote);
        }
        if (decision.blocksPhoto()) {
            blockPhoto(report, asset, adminUserId, processedAt, reason, adminNote);
        }

        markProcessed(report, DiaryCoverLibraryReportStatus.RESOLVED,
                decision.resolutionAction, adminUserId, processedAt, adminNote);
    }

    @Override
    @Transactional
    public Long restoreItem(Long adminUserId,
                            Long itemId,
                            DiaryCoverLibraryRestoreForm form) {
        requireAdmin(adminUserId);
        String reason = normalizeRestoreReason(form == null ? null : form.getReason());
        String adminNote = normalizeOptional(form == null ? null : form.getAdminNote());

        DiaryCoverLibraryItem item = itemMapper.findByIdForUpdate(itemId);
        if (item == null || item.getStatus() != DiaryCoverLibraryItemStatus.BLOCKED) {
            throw new DiaryCoverLibraryModerationException(
                    "차단된 표지만 복구할 수 있습니다.");
        }

        DiaryCoverLibraryModerationAction blockAction =
                actionMapper.findLatestBlockItemByLibraryItemId(item.getId());
        DiaryCoverLibraryItemStatus restoredStatus = restoredItemStatus(blockAction, item);
        if (itemMapper.restoreByAdmin(item.getId(), restoredStatus) != 1) {
            throw new DiaryCoverLibraryModerationException(
                    "표지 상태가 변경되어 복구할 수 없습니다.");
        }

        insertAction(blockAction.getReportId(), item.getId(), null,
                DiaryCoverLibraryModerationActionType.RESTORE_ITEM,
                DiaryCoverLibraryItemStatus.BLOCKED.name(), restoredStatus.name(),
                adminUserId, Timestamp.from(clock.instant()), reason, adminNote);
        return blockAction.getReportId();
    }

    @Override
    @Transactional
    public Long restorePhoto(Long adminUserId,
                             Long photoAssetId,
                             DiaryCoverLibraryRestoreForm form) {
        requireAdmin(adminUserId);
        String reason = normalizeRestoreReason(form == null ? null : form.getReason());
        String adminNote = normalizeOptional(form == null ? null : form.getAdminNote());

        DiaryCoverLibraryPhotoAsset asset = photoAssetMapper.findByIdForUpdate(photoAssetId);
        if (asset == null || asset.getStatus() != DiaryCoverLibraryPhotoAssetStatus.BLOCKED) {
            throw new DiaryCoverLibraryModerationException(
                    "차단된 사진만 복구할 수 있습니다.");
        }

        DiaryCoverLibraryModerationAction blockAction =
                actionMapper.findLatestBlockPhotoByPhotoAssetId(asset.getId());
        if (blockAction == null
                || blockAction.getReportId() == null
                || !asset.getLibraryItemId().equals(blockAction.getLibraryItemId())
                || !asset.getId().equals(blockAction.getPhotoAssetId())) {
            throw new DiaryCoverLibraryModerationException(
                    "사진 차단 이력을 확인할 수 없어 복구할 수 없습니다.");
        }
        if (photoAssetMapper.restoreByAdmin(asset.getId()) != 1) {
            throw new DiaryCoverLibraryModerationException(
                    "사진 상태가 변경되어 복구할 수 없습니다.");
        }

        insertAction(blockAction.getReportId(), asset.getLibraryItemId(), asset.getId(),
                DiaryCoverLibraryModerationActionType.RESTORE_PHOTO,
                DiaryCoverLibraryPhotoAssetStatus.BLOCKED.name(),
                DiaryCoverLibraryPhotoAssetStatus.ACTIVE.name(),
                adminUserId, Timestamp.from(clock.instant()), reason, adminNote);
        return blockAction.getReportId();
    }

    private void blockItem(DiaryCoverLibraryReport report,
                           DiaryCoverLibraryItem item,
                           Long adminUserId,
                           Timestamp now,
                           String reason,
                           String adminNote) {
        DiaryCoverLibraryItemStatus previous = item.getStatus();
        if (previous != DiaryCoverLibraryItemStatus.PUBLISHED
                && previous != DiaryCoverLibraryItemStatus.WITHDRAWN) {
            throw new DiaryCoverLibraryModerationException("현재 상태에서는 표지를 차단할 수 없습니다.");
        }
        if (itemMapper.blockByAdmin(item.getId(), previous, now, reason) != 1) {
            throw new DiaryCoverLibraryModerationException("표지 상태가 변경되어 처리할 수 없습니다.");
        }
        insertAction(report.getId(), report.getLibraryItemId(), null,
                DiaryCoverLibraryModerationActionType.BLOCK_ITEM,
                previous.name(), DiaryCoverLibraryItemStatus.BLOCKED.name(),
                adminUserId, now, reason, adminNote);
    }

    private void blockPhoto(DiaryCoverLibraryReport report,
                            DiaryCoverLibraryPhotoAsset asset,
                            Long adminUserId,
                            Timestamp now,
                            String reason,
                            String adminNote) {
        if (asset.getStatus() != DiaryCoverLibraryPhotoAssetStatus.ACTIVE) {
            throw new DiaryCoverLibraryModerationException("이미 차단되었거나 사용할 수 없는 사진입니다.");
        }
        if (photoAssetMapper.blockByAdmin(asset.getId(), now, reason) != 1) {
            throw new DiaryCoverLibraryModerationException("사진 상태가 변경되어 처리할 수 없습니다.");
        }
        insertAction(report.getId(), report.getLibraryItemId(), asset.getId(),
                DiaryCoverLibraryModerationActionType.BLOCK_PHOTO,
                DiaryCoverLibraryPhotoAssetStatus.ACTIVE.name(),
                DiaryCoverLibraryPhotoAssetStatus.BLOCKED.name(),
                adminUserId, now, reason, adminNote);
    }

    private DiaryCoverLibraryPhotoAsset lockTargetPhoto(
            DiaryCoverLibraryReport report, Long requestedPhotoAssetId) {
        if (report.getPhotoAssetId() == null
                || requestedPhotoAssetId == null
                || !report.getPhotoAssetId().equals(requestedPhotoAssetId)) {
            throw new DiaryCoverLibraryModerationException("신고 대상 사진이 일치하지 않습니다.");
        }
        DiaryCoverLibraryPhotoAsset asset =
                photoAssetMapper.findByIdForUpdate(report.getPhotoAssetId());
        if (asset == null
                || !report.getLibraryItemId().equals(asset.getLibraryItemId())
                || !report.getReportedSnapshotVersion().equals(asset.getSnapshotVersion())) {
            throw new DiaryCoverLibraryModerationException("신고 대상 사진이 일치하지 않습니다.");
        }
        return asset;
    }

    private DiaryCoverLibraryItemStatus restoredItemStatus(
            DiaryCoverLibraryModerationAction blockAction,
            DiaryCoverLibraryItem item) {
        if (blockAction == null
                || blockAction.getReportId() == null
                || !item.getId().equals(blockAction.getLibraryItemId())) {
            throw new DiaryCoverLibraryModerationException(
                    "표지 차단 이력을 확인할 수 없어 복구할 수 없습니다.");
        }
        return switch (blockAction.getPreviousStatus()) {
            case "PUBLISHED" -> DiaryCoverLibraryItemStatus.PUBLISHED;
            case "WITHDRAWN" -> DiaryCoverLibraryItemStatus.WITHDRAWN;
            default -> throw new DiaryCoverLibraryModerationException(
                    "표지를 복구할 원래 상태가 올바르지 않습니다.");
        };
    }

    private void insertAction(Long reportId,
                              Long libraryItemId,
                              Long photoAssetId,
                              DiaryCoverLibraryModerationActionType actionType,
                              String previousStatus,
                              String resultingStatus,
                              Long adminUserId,
                              Timestamp now,
                              String reason,
                              String adminNote) {
        DiaryCoverLibraryModerationAction action = new DiaryCoverLibraryModerationAction();
        action.setReportId(reportId);
        action.setLibraryItemId(libraryItemId);
        action.setPhotoAssetId(photoAssetId);
        action.setActionType(actionType);
        action.setPreviousStatus(previousStatus);
        action.setResultingStatus(resultingStatus);
        action.setReason(reason);
        action.setAdminNote(adminNote);
        action.setActionByUserId(adminUserId);
        action.setCreatedAt(now);
        if (actionMapper.insert(action) != 1) {
            throw new IllegalStateException("관리자 처리 이력을 저장하지 못했습니다.");
        }
    }

    private void markProcessed(DiaryCoverLibraryReport report,
                               DiaryCoverLibraryReportStatus status,
                               DiaryCoverLibraryResolutionAction resolutionAction,
                               Long adminUserId,
                               Timestamp processedAt,
                               String adminNote) {
        if (reportMapper.markProcessed(report.getId(), status, resolutionAction,
                adminUserId, processedAt, adminNote) != 1) {
            throw new DiaryCoverLibraryModerationException("신고 상태가 변경되어 처리할 수 없습니다.");
        }
    }

    private void requireAdmin(Long adminUserId) {
        User admin = adminUserId == null ? null : userMapper.findById(adminUserId);
        if (admin == null || admin.getUserRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "관리자만 신고를 처리할 수 있습니다.");
        }
    }

    private Decision parseDecision(String value) {
        try {
            return Decision.valueOf(
                    value == null ? "" : value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new DiaryCoverLibraryModerationException("처리 방법을 선택해 주세요.");
        }
    }

    private String normalizeReason(String value) {
        String reason = value == null ? "" : value.strip();
        if (reason.isEmpty()) {
            throw new DiaryCoverLibraryModerationException("차단 사유를 입력해 주세요.");
        }
        if (reason.length() > REASON_MAX_LENGTH) {
            throw new DiaryCoverLibraryModerationException("차단 사유는 500자 이하로 입력해 주세요.");
        }
        return reason;
    }

    private String normalizeRestoreReason(String value) {
        String reason = value == null ? "" : value.strip();
        if (reason.isEmpty()) {
            throw new DiaryCoverLibraryModerationException("복구 사유를 입력해 주세요.");
        }
        if (reason.length() > REASON_MAX_LENGTH) {
            throw new DiaryCoverLibraryModerationException(
                    "복구 사유는 500자 이하로 입력해 주세요.");
        }
        return reason;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private enum Decision {
        REJECT(false, false, DiaryCoverLibraryResolutionAction.NO_ACTION),
        BLOCK_ITEM(true, false, DiaryCoverLibraryResolutionAction.ITEM_BLOCKED),
        BLOCK_PHOTO(false, true, DiaryCoverLibraryResolutionAction.PHOTO_BLOCKED),
        BLOCK_ITEM_AND_PHOTO(true, true,
                DiaryCoverLibraryResolutionAction.ITEM_AND_PHOTO_BLOCKED);

        private final boolean blockItem;
        private final boolean blockPhoto;
        private final DiaryCoverLibraryResolutionAction resolutionAction;

        Decision(boolean blockItem,
                 boolean blockPhoto,
                 DiaryCoverLibraryResolutionAction resolutionAction) {
            this.blockItem = blockItem;
            this.blockPhoto = blockPhoto;
            this.resolutionAction = resolutionAction;
        }

        boolean blocksItem() {
            return blockItem;
        }

        boolean blocksPhoto() {
            return blockPhoto;
        }
    }
}
