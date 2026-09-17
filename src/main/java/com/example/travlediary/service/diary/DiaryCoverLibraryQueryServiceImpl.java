package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryAssetFile;
import com.example.travlediary.dto.DiaryCoverLibraryDetailDto;
import com.example.travlediary.dto.DiaryCoverLibraryMineDto;
import com.example.travlediary.dto.DiaryCoverLibraryPageDto;
import com.example.travlediary.dto.DiaryCoverLibrarySort;
import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAssetStatus;
import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import com.example.travlediary.repository.diary.DiaryCoverLibraryElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryItemMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryPhotoAssetMapper;
import com.example.travlediary.service.file.DiaryCoverLibraryPhotoStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DiaryCoverLibraryQueryServiceImpl implements DiaryCoverLibraryQueryService {

    private static final int PAGE_SIZE = 12;
    private static final String PHOTO = "PHOTO";
    private static final String ASSET_URL_PREFIX = "/diaries/cover-library/assets/";
    private static final Map<String, String> IMAGE_CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp");

    private final DiaryCoverLibraryItemMapper itemMapper;
    private final DiaryCoverLibraryElementMapper elementMapper;
    private final DiaryCoverLibraryPhotoAssetMapper photoAssetMapper;
    private final DiaryCoverLibraryPhotoStorage photoStorage;

    @Override
    @Transactional(readOnly = true)
    public DiaryCoverLibraryPageDto getPublishedPage(
            DiaryCoverLibrarySort sort, int page) {
        DiaryCoverLibrarySort order = sort == null ? DiaryCoverLibrarySort.DEFAULT : sort;
        int totalCount = itemMapper.countPublished();
        int totalPages = Math.max(1, (totalCount + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.min(Math.max(page, 1), totalPages);

        List<DiaryCoverLibraryItem> items = totalCount == 0 ? List.of()
                : itemMapper.findPublished(
                        order.name(), (currentPage - 1) * PAGE_SIZE, PAGE_SIZE);
        Map<Long, List<DiaryCoverLibraryElement>> elementsByItem =
                loadElementsByItem(items);
        return new DiaryCoverLibraryPageDto(
                items, elementsByItem, order,
                currentPage, totalPages, totalCount, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public DiaryCoverLibraryDetailDto getPublishedDetail(Long itemId) {
        DiaryCoverLibraryItem item = itemMapper.findPublishedById(itemId);
        if (item == null) {
            throw notFound("공유 표지를 찾을 수 없습니다.");
        }
        List<DiaryCoverLibraryElement> elements =
                elementMapper.findAllByLibraryItemIdAndSnapshotVersion(
                        item.getId(), item.getSnapshotVersion());
        preparePhotoUrls(elements);
        return new DiaryCoverLibraryDetailDto(item, List.copyOf(elements));
    }

    @Override
    @Transactional(readOnly = true)
    public DiaryCoverLibraryMineDto getMine(Long userId) {
        DiaryCoverValues.requireUser(userId);
        List<DiaryCoverLibraryItem> items =
                itemMapper.findManageableByCreatorUserId(userId);
        return new DiaryCoverLibraryMineDto(
                List.copyOf(items), loadElementsByItem(items));
    }

    @Override
    @Transactional(readOnly = true)
    public DiaryCoverLibraryAssetFile getActiveAsset(Long assetId) {
        DiaryCoverLibraryPhotoAsset asset = photoAssetMapper.findById(assetId);
        if (asset == null || asset.getStatus() != DiaryCoverLibraryPhotoAssetStatus.ACTIVE) {
            throw notFound("공유 사진을 찾을 수 없습니다.");
        }
        if (!matchesContentType(asset.getStorageKey(), asset.getContentType())
                || asset.getFileSize() == null || asset.getFileSize() <= 0) {
            throw notFound("공유 사진을 찾을 수 없습니다.");
        }

        Path path;
        try {
            path = photoStorage.resolveForRead(asset.getStorageKey());
        } catch (RuntimeException exception) {
            throw notFound("공유 사진을 찾을 수 없습니다.");
        }
        return new DiaryCoverLibraryAssetFile(
                path, asset.getContentType(), asset.getFileSize());
    }

    private Map<Long, List<DiaryCoverLibraryElement>> loadElementsByItem(
            List<DiaryCoverLibraryItem> items) {
        Map<Long, List<DiaryCoverLibraryElement>> grouped = new LinkedHashMap<>();
        for (DiaryCoverLibraryItem item : items) {
            grouped.put(item.getId(), new ArrayList<>());
        }
        if (items.isEmpty()) {
            return Map.of();
        }

        List<Long> itemIds = items.stream().map(DiaryCoverLibraryItem::getId).toList();
        List<DiaryCoverLibraryElement> elements =
                elementMapper.findAllByLibraryItemIds(itemIds);
        preparePhotoUrls(elements);
        for (DiaryCoverLibraryElement element : elements) {
            List<DiaryCoverLibraryElement> itemElements = grouped.get(element.getLibraryItemId());
            if (itemElements != null) {
                itemElements.add(element);
            }
        }

        Map<Long, List<DiaryCoverLibraryElement>> immutable = new LinkedHashMap<>();
        grouped.forEach((itemId, itemElements) ->
                immutable.put(itemId, List.copyOf(itemElements)));
        return Map.copyOf(immutable);
    }

    private void preparePhotoUrls(List<DiaryCoverLibraryElement> elements) {
        for (DiaryCoverLibraryElement element : elements) {
            if (!PHOTO.equals(element.getElementType())) {
                continue;
            }
            if (element.getPhotoShareMode() == DiaryCoverLibraryPhotoShareMode.INCLUDED
                    && element.getPhotoAssetId() != null) {
                element.setImageUrl(ASSET_URL_PREFIX + element.getPhotoAssetId());
            } else {
                element.setImageUrl(null);
            }
        }
    }

    private boolean matchesContentType(String storageKey, String contentType) {
        if (storageKey == null || contentType == null) {
            return false;
        }
        String normalized = storageKey.toLowerCase(Locale.ROOT);
        int extensionStart = normalized.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == normalized.length() - 1) {
            return false;
        }
        return contentType.equals(IMAGE_CONTENT_TYPES.get(normalized.substring(extensionStart + 1)));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
