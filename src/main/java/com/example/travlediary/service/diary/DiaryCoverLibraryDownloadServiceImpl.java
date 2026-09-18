package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryDownloadResult;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.model.DiaryCoverLibraryDownload;
import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAssetStatus;
import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import com.example.travlediary.repository.diary.DiaryCoverDesignElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverDesignMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryDownloadMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryItemMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryPhotoAssetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DiaryCoverLibraryDownloadServiceImpl
        implements DiaryCoverLibraryDownloadService {

    private static final int DESIGN_NAME_MAX_LENGTH = 50;
    private static final String PHOTO = "PHOTO";

    private final CoverLibraryAccessService accessService;
    private final DiaryCoverLibraryItemMapper itemMapper;
    private final DiaryCoverLibraryElementMapper libraryElementMapper;
    private final DiaryCoverLibraryPhotoAssetMapper photoAssetMapper;
    private final DiaryCoverDesignMapper designMapper;
    private final DiaryCoverDesignElementMapper designElementMapper;
    private final DiaryCoverLibraryDownloadMapper downloadMapper;
    private final Clock clock;

    @Autowired
    public DiaryCoverLibraryDownloadServiceImpl(
            CoverLibraryAccessService accessService,
            DiaryCoverLibraryItemMapper itemMapper,
            DiaryCoverLibraryElementMapper libraryElementMapper,
            DiaryCoverLibraryPhotoAssetMapper photoAssetMapper,
            DiaryCoverDesignMapper designMapper,
            DiaryCoverDesignElementMapper designElementMapper,
            DiaryCoverLibraryDownloadMapper downloadMapper) {
        this(accessService, itemMapper, libraryElementMapper, photoAssetMapper,
                designMapper, designElementMapper, downloadMapper, Clock.systemUTC());
    }

    DiaryCoverLibraryDownloadServiceImpl(
            CoverLibraryAccessService accessService,
            DiaryCoverLibraryItemMapper itemMapper,
            DiaryCoverLibraryElementMapper libraryElementMapper,
            DiaryCoverLibraryPhotoAssetMapper photoAssetMapper,
            DiaryCoverDesignMapper designMapper,
            DiaryCoverDesignElementMapper designElementMapper,
            DiaryCoverLibraryDownloadMapper downloadMapper,
            Clock clock) {
        this.accessService = accessService;
        this.itemMapper = itemMapper;
        this.libraryElementMapper = libraryElementMapper;
        this.photoAssetMapper = photoAssetMapper;
        this.designMapper = designMapper;
        this.designElementMapper = designElementMapper;
        this.downloadMapper = downloadMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DiaryCoverLibraryDownloadResult download(Long userId, Long libraryItemId) {
        DiaryCoverValues.requireUser(userId);
        if (!accessService.canDownload(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "표지 디자인을 받을 권한이 없습니다.");
        }
        if (libraryItemId == null) {
            throw notFound();
        }

        DiaryCoverLibraryItem item = itemMapper.findPublishedByIdForUpdate(libraryItemId);
        if (item == null) {
            throw notFound();
        }
        // 직접 공유한 표지는 원본을 이미 가지고 있으므로 받지 않는다. (이력·다운로드 수도 건드리지 않는다)
        if (userId.equals(item.getCreatorUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "직접 공유한 디자인은 다시 받을 수 없습니다.");
        }
        /*
          이 표지에서 받은 디자인을 지금 가지고 있으면 또 만들지 않는다.
          표지 행을 잠근 뒤에 세므로 같은 회원의 동시 요청도 한 번만 통과한다.
          다운로드 이력이 아니라 현재 보유 여부로 본다 — 받은 디자인을 지웠다면 다시 받을 수 있다.
        */
        if (designMapper.countByUserIdAndSourceLibraryItemId(userId, item.getId()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "이미 내 보유 디자인에 있는 표지입니다.");
        }
        List<DiaryCoverLibraryElement> snapshot =
                libraryElementMapper.findAllByLibraryItemIdAndSnapshotVersion(
                        item.getId(), item.getSnapshotVersion());
        Set<Long> activePhotoAssetIds = photoAssetMapper
                .findAllByLibraryItemIdAndSnapshotVersionForUpdate(
                        item.getId(), item.getSnapshotVersion())
                .stream()
                .filter(asset -> asset.getStatus() == DiaryCoverLibraryPhotoAssetStatus.ACTIVE)
                .map(DiaryCoverLibraryPhotoAsset::getId)
                .collect(Collectors.toUnmodifiableSet());

        DiaryCoverDesign design = copyDesign(item, userId);
        for (DiaryCoverLibraryElement source : snapshot) {
            DiaryCoverDesignElement copied = copyElement(source, design.getId(), activePhotoAssetIds);
            if (designElementMapper.insert(copied) != 1) {
                throw new IllegalStateException("라이브러리 표지 요소를 복사하지 못했습니다.");
            }
        }

        DiaryCoverLibraryDownload history = new DiaryCoverLibraryDownload();
        history.setLibraryItemId(item.getId());
        history.setDownloaderUserId(userId);
        history.setFirstDownloadedAt(Timestamp.from(clock.instant()));
        // 회원별 첫 다운로드만 센다. (다시 받은 경우 이력이 이미 있어 늘지 않는다)
        boolean counted = downloadMapper.insertIgnore(history) == 1;
        if (counted && itemMapper.incrementDownloadCountIfPublished(item.getId()) != 1) {
            throw new IllegalStateException("라이브러리 다운로드 수를 갱신하지 못했습니다.");
        }
        // 잠근 채로 읽은 값에 이번에 실제로 더한 만큼만 반영해 돌려준다. (화면이 그대로 쓴다)
        long lockedCount = item.getDownloadCount() == null ? 0L : item.getDownloadCount();
        return new DiaryCoverLibraryDownloadResult(design, counted ? lockedCount + 1 : lockedCount);
    }

    private DiaryCoverDesign copyDesign(DiaryCoverLibraryItem item, Long userId) {
        DiaryCoverDesign design = new DiaryCoverDesign();
        design.setUserId(userId);
        design.setSourceLibraryItemId(item.getId());
        design.setName(designName(item.getTitle()));
        design.setBaseCoverStyle(item.getBaseCoverStyle());
        design.setBackgroundColor(item.getBackgroundColor());
        if (designMapper.insert(design) != 1 || design.getId() == null) {
            throw new IllegalStateException("내 표지 디자인을 만들지 못했습니다.");
        }
        return design;
    }

    private DiaryCoverDesignElement copyElement(
            DiaryCoverLibraryElement source, Long designId, Set<Long> activePhotoAssetIds) {
        DiaryCoverDesignElement copied = new DiaryCoverDesignElement();
        copied.setDesignId(designId);
        copied.setElementType(source.getElementType());
        copied.setTextContent(source.getTextContent());
        copied.setImageUrl(source.getImageUrl());
        copied.setStyleType(source.getStyleType());
        copied.setColorType(source.getColorType());
        copied.setPhotoStyle(source.getPhotoStyle());
        copied.setTextFont(source.getTextFont());
        copied.setTextColor(source.getTextColor());
        copied.setPositionX(source.getPositionX());
        copied.setPositionY(source.getPositionY());
        copied.setWidth(source.getWidth());
        copied.setHeight(source.getHeight());
        copied.setRotation(source.getRotation());
        copied.setZIndex(source.getZIndex());

        if (PHOTO.equals(source.getElementType())) {
            copied.setImageUrl(null);
            Long assetId = source.getPhotoShareMode() == DiaryCoverLibraryPhotoShareMode.INCLUDED
                    ? source.getPhotoAssetId() : null;
            copied.setLibraryPhotoAssetId(
                    assetId != null && activePhotoAssetIds.contains(assetId) ? assetId : null);
        }
        return copied;
    }

    private String designName(String title) {
        String normalized = title == null ? "라이브러리 표지" : title.strip();
        if (normalized.isEmpty()) {
            normalized = "라이브러리 표지";
        }
        return normalized.length() <= DESIGN_NAME_MAX_LENGTH
                ? normalized : normalized.substring(0, DESIGN_NAME_MAX_LENGTH);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, "공개된 표지 디자인을 찾을 수 없습니다.");
    }
}
