package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryPhotoSelection;
import com.example.travlediary.dto.DiaryCoverLibraryRegistrationRequest;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryItemStatus;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAssetStatus;
import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import com.example.travlediary.model.DiaryCoverPhotoStyle;
import com.example.travlediary.model.User;
import com.example.travlediary.repository.diary.DiaryCoverDesignElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverDesignMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryItemMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryPhotoAssetMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.file.DiaryCoverLibraryPhotoStorage;
import com.example.travlediary.service.file.DiaryPrivatePhotoStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DiaryCoverLibraryRegistrationServiceImpl
        implements DiaryCoverLibraryRegistrationService {

    private static final int INITIAL_SNAPSHOT_VERSION = 1;
    private static final int TITLE_MAX_LENGTH = 50;
    private static final String PHOTO = "PHOTO";

    private final DiaryCoverLibraryItemMapper itemMapper;
    private final DiaryCoverLibraryPhotoAssetMapper photoAssetMapper;
    private final DiaryCoverLibraryElementMapper elementMapper;
    private final DiaryCoverDesignMapper designMapper;
    private final DiaryCoverDesignElementMapper designElementMapper;
    private final UserMapper userMapper;
    private final DiaryCoverLibraryPhotoStorage photoStorage;
    /** 공유할 원본이 있는 자리. 내 표지 디자인 사진은 공개 업로드 폴더를 떠나 여기에 있다. */
    private final DiaryPrivatePhotoStorage diaryPhotoStorage;
    private final Clock clock;

    @Autowired
    public DiaryCoverLibraryRegistrationServiceImpl(
            DiaryCoverLibraryItemMapper itemMapper,
            DiaryCoverLibraryPhotoAssetMapper photoAssetMapper,
            DiaryCoverLibraryElementMapper elementMapper,
            DiaryCoverDesignMapper designMapper,
            DiaryCoverDesignElementMapper designElementMapper,
            UserMapper userMapper,
            DiaryCoverLibraryPhotoStorage photoStorage,
            DiaryPrivatePhotoStorage diaryPhotoStorage) {
        this(itemMapper, photoAssetMapper, elementMapper, designMapper, designElementMapper,
                userMapper, photoStorage, diaryPhotoStorage, Clock.systemUTC());
    }

    DiaryCoverLibraryRegistrationServiceImpl(
            DiaryCoverLibraryItemMapper itemMapper,
            DiaryCoverLibraryPhotoAssetMapper photoAssetMapper,
            DiaryCoverLibraryElementMapper elementMapper,
            DiaryCoverDesignMapper designMapper,
            DiaryCoverDesignElementMapper designElementMapper,
            UserMapper userMapper,
            DiaryCoverLibraryPhotoStorage photoStorage,
            DiaryPrivatePhotoStorage diaryPhotoStorage,
            Clock clock) {
        this.itemMapper = itemMapper;
        this.photoAssetMapper = photoAssetMapper;
        this.elementMapper = elementMapper;
        this.designMapper = designMapper;
        this.designElementMapper = designElementMapper;
        this.userMapper = userMapper;
        this.photoStorage = photoStorage;
        this.diaryPhotoStorage = diaryPhotoStorage;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DiaryCoverLibraryItem register(Long userId,
                                          DiaryCoverLibraryRegistrationRequest request) {
        DiaryCoverValues.requireUser(userId);
        if (request == null || request.sourceCoverDesignId() == null) {
            throw badRequest("공유할 표지 디자인을 선택해 주세요.");
        }

        DiaryCoverDesign design = designMapper.findByIdAndUserId(
                request.sourceCoverDesignId(), userId);
        if (design == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "표지 디자인을 찾을 수 없습니다.");
        }
        // 다른 회원의 표지를 받아 내 것처럼 다시 올리지 못하게 한다. 받은 뒤 편집했어도 출처는 남아 있다.
        if (design.getSourceLibraryItemId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, LIBRARY_SOURCED_SHARE_MESSAGE);
        }

        List<DiaryCoverDesignElement> sourceElements =
                designElementMapper.findAllByDesignId(design.getId());
        Map<Long, DiaryCoverLibraryPhotoSelection> selections =
                request.photoSelections() == null ? Map.of() : request.photoSelections();
        validatePhotoSelections(sourceElements, selections);

        User creator = userMapper.findById(userId);
        if (creator == null || creator.getNickname() == null || creator.getNickname().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }

        DiaryCoverLibraryItem item = createItem(design, creator, request);
        List<String> newStorageKeys = new ArrayList<>();
        boolean rollbackCleanupRegistered = false;
        try {
            for (DiaryCoverDesignElement source : sourceElements) {
                DiaryCoverLibraryElement snapshot = copyElementShell(source, item.getId());
                if (PHOTO.equals(source.getElementType())) {
                    DiaryCoverLibraryPhotoSelection selection = selections.get(source.getId());
                    DiaryCoverLibraryPhotoShareMode mode = selection == null
                            || selection.mode() == null
                            ? DiaryCoverLibraryPhotoShareMode.EXCLUDED
                            : selection.mode();
                    snapshot.setPhotoStyle(DiaryCoverPhotoStyle.of(source.getPhotoStyle()).getCode());
                    snapshot.setImageUrl(null);
                    snapshot.setPhotoShareMode(mode);
                    if (mode == DiaryCoverLibraryPhotoShareMode.INCLUDED) {
                        DiaryCoverLibraryPhotoAsset asset = createPhotoAsset(
                                source, item, creator, newStorageKeys);
                        snapshot.setPhotoAssetId(asset.getId());
                        if (!rollbackCleanupRegistered) {
                            rollbackCleanupRegistered = registerRollbackCleanup(newStorageKeys);
                        }
                    }
                }
                if (elementMapper.insert(snapshot) != 1) {
                    throw new IllegalStateException("라이브러리 표지 요소를 저장하지 못했습니다.");
                }
            }
            return item;
        } catch (RuntimeException exception) {
            if (!rollbackCleanupRegistered) {
                deleteSafely(newStorageKeys);
            }
            throw exception;
        }
    }

    private DiaryCoverLibraryItem createItem(
            DiaryCoverDesign design, User creator,
            DiaryCoverLibraryRegistrationRequest request) {
        DiaryCoverLibraryItem item = new DiaryCoverLibraryItem();
        item.setCreatorUserId(creator.getId());
        item.setCreatorDisplayName(creator.getNickname());
        item.setSourceCoverDesignId(design.getId());
        item.setTitle(normalizeTitle(request.title()));
        item.setDescription(normalizeDescription(request.description()));
        item.setBaseCoverStyle(design.getBaseCoverStyle());
        item.setBackgroundColor(design.getBackgroundColor());
        item.setStatus(DiaryCoverLibraryItemStatus.PUBLISHED);
        item.setDownloadCount(0L);
        item.setSnapshotVersion(INITIAL_SNAPSHOT_VERSION);
        if (itemMapper.insert(item) != 1 || item.getId() == null) {
            throw new IllegalStateException("표지 라이브러리 항목을 저장하지 못했습니다.");
        }
        return item;
    }

    private DiaryCoverLibraryPhotoAsset createPhotoAsset(
            DiaryCoverDesignElement source,
            DiaryCoverLibraryItem item,
            User creator,
            List<String> newStorageKeys) {
        /*
          공유 원본은 사용자가 넘긴 경로가 아니라 저장된 요소의 저장 키다.
          개인 사진 저장소가 관리 키인지 확인해 실제 경로를 내주고, 그 경로만 복사한다.
        */
        DiaryCoverLibraryPhotoStorage.StoredPhoto stored = photoStorage
                .copyFromDiaryPrivateStorage(
                        diaryPhotoStorage.resolveManagedSource(source.getImageUrl()));
        newStorageKeys.add(stored.storageKey());

        DiaryCoverLibraryPhotoAsset asset = new DiaryCoverLibraryPhotoAsset();
        asset.setLibraryItemId(item.getId());
        asset.setOriginalUploaderUserId(creator.getId());
        asset.setOriginalUploaderDisplayName(creator.getNickname());
        asset.setSnapshotVersion(INITIAL_SNAPSHOT_VERSION);
        asset.setStorageKey(stored.storageKey());
        asset.setContentType(stored.contentType());
        asset.setFileSize(stored.fileSize());
        asset.setStatus(DiaryCoverLibraryPhotoAssetStatus.ACTIVE);
        asset.setRightsConfirmedAt(Timestamp.from(clock.instant()));
        asset.setRightsTermsVersion(CoverLibraryPhotoRightsPolicy.CURRENT_TERMS_VERSION);
        if (photoAssetMapper.insert(asset) != 1 || asset.getId() == null) {
            throw new IllegalStateException("공유 사진 자산을 저장하지 못했습니다.");
        }
        return asset;
    }

    private DiaryCoverLibraryElement copyElementShell(
            DiaryCoverDesignElement source, Long libraryItemId) {
        DiaryCoverLibraryElement snapshot = new DiaryCoverLibraryElement();
        snapshot.setLibraryItemId(libraryItemId);
        snapshot.setSnapshotVersion(INITIAL_SNAPSHOT_VERSION);
        snapshot.setElementType(source.getElementType());
        snapshot.setTextContent(source.getTextContent());
        snapshot.setImageUrl(source.getImageUrl());
        snapshot.setStyleType(source.getStyleType());
        snapshot.setColorType(source.getColorType());
        snapshot.setPhotoStyle(source.getPhotoStyle());
        snapshot.setTextFont(source.getTextFont());
        snapshot.setTextColor(source.getTextColor());
        snapshot.setPositionX(source.getPositionX());
        snapshot.setPositionY(source.getPositionY());
        snapshot.setWidth(source.getWidth());
        snapshot.setHeight(source.getHeight());
        snapshot.setRotation(source.getRotation());
        snapshot.setZIndex(source.getZIndex());
        return snapshot;
    }

    private void validatePhotoSelections(
            List<DiaryCoverDesignElement> elements,
            Map<Long, DiaryCoverLibraryPhotoSelection> selections) {
        Map<Long, DiaryCoverDesignElement> photos = new HashMap<>();
        for (DiaryCoverDesignElement element : elements) {
            if (PHOTO.equals(element.getElementType())) {
                photos.put(element.getId(), element);
            }
        }

        for (Map.Entry<Long, DiaryCoverLibraryPhotoSelection> entry : selections.entrySet()) {
            if (entry.getKey() == null || !photos.containsKey(entry.getKey())) {
                throw badRequest("사진 공유 대상을 다시 선택해 주세요.");
            }
            DiaryCoverLibraryPhotoSelection selection = entry.getValue();
            DiaryCoverLibraryPhotoShareMode mode = selection == null || selection.mode() == null
                    ? DiaryCoverLibraryPhotoShareMode.EXCLUDED
                    : selection.mode();
            if (mode == DiaryCoverLibraryPhotoShareMode.INCLUDED
                    && photos.get(entry.getKey()).getLibraryPhotoAssetId() != null) {
                throw badRequest("받은 공유 사진은 사진 제외로만 다시 공유할 수 있습니다.");
            }
            if (mode == DiaryCoverLibraryPhotoShareMode.INCLUDED
                    && (selection == null || !selection.rightsConfirmed())) {
                throw badRequest("사진 공유 권리를 확인해 주세요.");
            }
        }
    }

    private boolean registerRollbackCleanup(List<String> storageKeys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteSafely(storageKeys);
                }
            }
        });
        return true;
    }

    private void deleteSafely(List<String> storageKeys) {
        for (String storageKey : storageKeys) {
            try {
                photoStorage.delete(storageKey);
            } catch (RuntimeException exception) {
                log.warn("롤백된 표지 라이브러리 사진을 정리하지 못했습니다: {}",
                        storageKey, exception);
            }
        }
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw badRequest("라이브러리 제목을 입력해 주세요.");
        }
        String normalized = title.strip();
        if (normalized.length() > TITLE_MAX_LENGTH) {
            throw badRequest("라이브러리 제목은 50자 이하로 입력해 주세요.");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.strip();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
