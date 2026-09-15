package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiarySticker;
import com.example.travlediary.model.DiaryStickerAccessTier;
import com.example.travlediary.model.DiaryStickerCatalogItem;
import com.example.travlediary.model.DiaryStickerCategory;
import com.example.travlediary.model.DiaryStickerCategoryEntity;
import com.example.travlediary.model.DiaryStickerCollection;
import com.example.travlediary.model.DiaryStickerRepeat;
import com.example.travlediary.model.DiaryStickerType;
import com.example.travlediary.repository.diary.DiaryStickerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** DB에서 사용자 편집 화면의 스티커 카탈로그를 구성한다. */
@Service
@RequiredArgsConstructor
public class DiaryStickerCatalog {

    private final DiaryStickerMapper diaryStickerMapper;

    @Transactional(readOnly = true)
    public Optional<DiarySticker> find(String stickerId) {
        if (stickerId == null || stickerId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(diaryStickerMapper.findVisibleByCatalogKey(stickerId.strip()))
                .map(this::toSticker);
    }

    /** 숨김 이후에도 체험 가져오기와 기존 저장 요소 검증이 가능해야 한다. */
    @Transactional(readOnly = true)
    public Optional<DiarySticker> findByImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.contains("..")) {
            return Optional.empty();
        }
        return Optional.ofNullable(diaryStickerMapper.findByImageUrl(imageUrl.strip()))
                .map(this::toSticker);
    }

    @Transactional(readOnly = true)
    public List<DiaryStickerCategory> getCategories() {
        List<DiaryStickerCatalogItem> stickers = safe(diaryStickerMapper.findVisibleStickers());
        Map<Long, List<DiarySticker>> byCategory = new LinkedHashMap<>();
        for (DiaryStickerCatalogItem item : stickers) {
            byCategory.computeIfAbsent(item.getCategoryId(), ignored -> new ArrayList<>())
                    .add(toSticker(item));
        }

        List<DiaryStickerCategory> result = new ArrayList<>();
        for (DiaryStickerCategoryEntity category : safe(diaryStickerMapper.findVisibleCategories())) {
            List<DiarySticker> owned = byCategory.getOrDefault(category.getId(), List.of());
            if (!owned.isEmpty()) {
                result.add(new DiaryStickerCategory(
                        category.getCode(), category.getName(), List.copyOf(owned)));
            }
        }
        return List.copyOf(result);
    }

    /** 숨긴 기존 테이프도 저장된 image_url로 계속 반복 렌더링한다. */
    @Transactional(readOnly = true)
    public Map<String, DiaryStickerRepeat> getRepeatsByImageUrl() {
        Map<String, DiaryStickerRepeat> result = new LinkedHashMap<>();
        for (DiaryStickerCatalogItem item : safe(diaryStickerMapper.findAllStickers())) {
            if (item.hasRepeatImages()) {
                result.put(item.getImageUrl(), new DiaryStickerRepeat(
                        item.getRepeatLeftUrl(), item.getRepeatCenterUrl(), item.getRepeatRightUrl()));
            }
        }
        return Map.copyOf(result);
    }

    private DiarySticker toSticker(DiaryStickerCatalogItem item) {
        DiaryStickerCollection collection = DiaryStickerCollection
                .fromCode(item.getCollectionCode()).orElse(DiaryStickerCollection.DEFAULT);
        DiaryStickerRepeat repeat = item.hasRepeatImages()
                ? new DiaryStickerRepeat(item.getRepeatLeftUrl(), item.getRepeatCenterUrl(),
                item.getRepeatRightUrl())
                : null;
        DiaryStickerType type = item.getStickerType() == null
                ? DiaryStickerType.NORMAL : item.getStickerType();
        DiaryStickerAccessTier tier = item.getAccessTier() == null
                ? DiaryStickerAccessTier.FREE : item.getAccessTier();
        String tapeStyle = item.getTapeStyle() == null || item.getTapeStyle().isBlank()
                ? DiarySticker.TAPE_NORMAL : item.getTapeStyle();
        return new DiarySticker(item.getCatalogKey(), item.getName(), item.getCategoryCode(),
                item.getImageUrl(), collection, tapeStyle, repeat, type, tier);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
