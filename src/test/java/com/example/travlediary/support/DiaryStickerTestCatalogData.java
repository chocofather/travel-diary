package com.example.travlediary.support;

import com.example.travlediary.model.DiaryStickerAccessTier;
import com.example.travlediary.model.DiaryStickerCatalogItem;
import com.example.travlediary.model.DiaryStickerCategoryEntity;
import com.example.travlediary.model.DiaryStickerType;
import com.example.travlediary.repository.diary.DiaryStickerMapper;

import java.util.List;

import static org.mockito.Mockito.when;

public final class DiaryStickerTestCatalogData {
    private DiaryStickerTestCatalogData() {
    }

    public static void stub(DiaryStickerMapper mapper) {
        List<DiaryStickerCategoryEntity> categories = List.of(
                category(1L, "travel", "여행", 1),
                category(2L, "landmark", "랜드마크", 2),
                category(3L, "emotion", "감정", 3),
                category(4L, "masking-tape", "마스킹테이프", 4));
        List<DiaryStickerCatalogItem> stickers = List.of(
                sticker("airplane", "비행기", 1L, "travel",
                        "/images/diary/stickers/travel/airplane.svg", DiaryStickerType.NORMAL,
                        "default", "NORMAL", null),
                sticker("eiffel-tower", "에펠탑", 2L, "landmark",
                        "/images/diary/stickers/travel/realistic/eiffel-tower.png", DiaryStickerType.NORMAL,
                        "realistic", "NORMAL", null),
                sticker("heart", "하트", 3L, "emotion",
                        "/images/diary/stickers/emotion/heart.svg", DiaryStickerType.NORMAL,
                        "default", "NORMAL", null),
                tape("tape-cloud-sky", "구름 하늘 테이프", "NORMAL"),
                tape("tape-cat-cream", "고양이 테이프", "NORMAL"),
                tape("tape-clear-star", "반투명 별 테이프", "TRANSLUCENT"),
                tape("tape-glass-star", "클리어 별 테이프", "CLEAR"));
        stickers.get(0).setAccessTier(DiaryStickerAccessTier.PREMIUM);
        when(mapper.findVisibleCategories()).thenReturn(categories);
        when(mapper.findAllCategories()).thenReturn(categories);
        when(mapper.findVisibleStickers()).thenReturn(stickers);
        when(mapper.findAllStickers()).thenReturn(stickers);
        when(mapper.findVisibleByCatalogKey(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> stickers.stream()
                        .filter(item -> item.getCatalogKey().equals(call.getArgument(0)))
                        .findFirst().orElse(null));
        when(mapper.findByImageUrl(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> stickers.stream()
                        .filter(item -> item.getImageUrl().equals(call.getArgument(0)))
                        .findFirst().orElse(null));
    }

    private static DiaryStickerCatalogItem tape(String key, String name, String style) {
        String base = "/images/diary/stickers/masking-tape/" + key;
        DiaryStickerCatalogItem item = sticker(key, name, 4L, "masking-tape",
                base + ".svg", DiaryStickerType.MASKING_TAPE, "default", style, key);
        return item;
    }

    private static DiaryStickerCatalogItem sticker(String key, String name, Long categoryId,
                                                    String categoryCode, String imageUrl,
                                                    DiaryStickerType type, String collection,
                                                    String tapeStyle, String repeatKey) {
        DiaryStickerCatalogItem item = new DiaryStickerCatalogItem();
        item.setId((long) Math.abs(key.hashCode()));
        item.setCatalogKey(key);
        item.setName(name);
        item.setCategoryId(categoryId);
        item.setCategoryCode(categoryCode);
        item.setImageUrl(imageUrl);
        item.setStickerType(type);
        item.setAccessTier(DiaryStickerAccessTier.FREE);
        item.setVisible(true);
        item.setDisplayOrder(1);
        item.setCollectionCode(collection);
        item.setTapeStyle(tapeStyle);
        if (repeatKey != null) {
            String repeatBase = "/images/diary/stickers/masking-tape/repeat/" + repeatKey;
            item.setRepeatLeftUrl(repeatBase + "-left.svg");
            item.setRepeatCenterUrl(repeatBase + "-center.svg");
            item.setRepeatRightUrl(repeatBase + "-right.svg");
        }
        return item;
    }

    private static DiaryStickerCategoryEntity category(Long id, String code, String name, int order) {
        DiaryStickerCategoryEntity category = new DiaryStickerCategoryEntity();
        category.setId(id);
        category.setCode(code);
        category.setName(name);
        category.setDisplayOrder(order);
        category.setVisible(true);
        return category;
    }
}
