package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryStickerAccessTier;
import com.example.travlediary.model.DiaryStickerCatalogItem;
import com.example.travlediary.model.DiaryStickerCategory;
import com.example.travlediary.model.DiaryStickerCategoryEntity;
import com.example.travlediary.model.DiaryStickerType;
import com.example.travlediary.repository.diary.DiaryStickerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryStickerCatalogDatabaseTest {

    @Mock
    private DiaryStickerMapper mapper;

    @Test
    void pickerUsesOnlyVisibleDatabaseCategoriesAndStickersInDisplayOrder() {
        DiaryStickerCategoryEntity travel = category(1L, "travel", "여행");
        DiaryStickerCatalogItem airplane = sticker(
                10L, "airplane", "비행기", 1L,
                "/images/diary/stickers/travel/airplane.svg",
                DiaryStickerType.NORMAL, DiaryStickerAccessTier.PREMIUM, true);
        when(mapper.findVisibleCategories()).thenReturn(List.of(travel));
        when(mapper.findVisibleStickers()).thenReturn(List.of(airplane));

        DiaryStickerCatalog catalog = new DiaryStickerCatalog(mapper);

        assertThat(catalog.getCategories())
                .extracting(DiaryStickerCategory::id)
                .containsExactly("travel");
        assertThat(catalog.getCategories().get(0).stickers())
                .singleElement()
                .satisfies(sticker -> {
                    assertThat(sticker.id()).isEqualTo("airplane");
                    assertThat(sticker.imageUrl()).isEqualTo(
                            "/images/diary/stickers/travel/airplane.svg");
                    assertThat(sticker.accessTier()).isEqualTo(DiaryStickerAccessTier.PREMIUM);
                });
    }

    @Test
    void hiddenStickerCannotBeAttachedButItsSavedImageUrlStillResolves() {
        DiaryStickerCatalogItem hidden = sticker(
                11L, "hidden-heart", "숨긴 하트", 1L,
                "/uploads/diary-stickers/normal/heart.png",
                DiaryStickerType.NORMAL, DiaryStickerAccessTier.FREE, false);
        when(mapper.findVisibleByCatalogKey("hidden-heart")).thenReturn(null);
        when(mapper.findByImageUrl(hidden.getImageUrl())).thenReturn(hidden);

        DiaryStickerCatalog catalog = new DiaryStickerCatalog(mapper);

        assertThat(catalog.find("hidden-heart")).isEmpty();
        assertThat(catalog.findByImageUrl(hidden.getImageUrl()))
                .hasValueSatisfying(sticker -> assertThat(sticker.id()).isEqualTo("hidden-heart"));
    }

    @Test
    void repeatMetadataRemainsAvailableForHiddenLegacyTape() {
        DiaryStickerCatalogItem tape = sticker(
                12L, "old-tape", "기존 테이프", 2L,
                "/images/diary/stickers/masking-tape/old.svg",
                DiaryStickerType.MASKING_TAPE, DiaryStickerAccessTier.FREE, false);
        tape.setRepeatLeftUrl("/images/diary/stickers/masking-tape/repeat/old-left.svg");
        tape.setRepeatCenterUrl("/images/diary/stickers/masking-tape/repeat/old-center.svg");
        tape.setRepeatRightUrl("/images/diary/stickers/masking-tape/repeat/old-right.svg");
        when(mapper.findAllStickers()).thenReturn(List.of(tape));

        DiaryStickerCatalog catalog = new DiaryStickerCatalog(mapper);

        assertThat(catalog.getRepeatsByImageUrl())
                .containsKey(tape.getImageUrl());
        assertThat(catalog.getRepeatsByImageUrl().get(tape.getImageUrl()).centerUrl())
                .isEqualTo(tape.getRepeatCenterUrl());
    }

    private DiaryStickerCategoryEntity category(Long id, String code, String name) {
        DiaryStickerCategoryEntity category = new DiaryStickerCategoryEntity();
        category.setId(id);
        category.setCode(code);
        category.setName(name);
        category.setVisible(true);
        category.setDisplayOrder(1);
        return category;
    }

    private DiaryStickerCatalogItem sticker(Long id, String key, String name, Long categoryId,
                                            String imageUrl, DiaryStickerType type,
                                            DiaryStickerAccessTier tier, boolean visible) {
        DiaryStickerCatalogItem sticker = new DiaryStickerCatalogItem();
        sticker.setId(id);
        sticker.setCatalogKey(key);
        sticker.setName(name);
        sticker.setCategoryId(categoryId);
        sticker.setCategoryCode(type == DiaryStickerType.MASKING_TAPE ? "masking-tape" : "travel");
        sticker.setImageUrl(imageUrl);
        sticker.setStickerType(type);
        sticker.setAccessTier(tier);
        sticker.setVisible(visible);
        sticker.setDisplayOrder(1);
        sticker.setCollectionCode("default");
        sticker.setTapeStyle("NORMAL");
        return sticker;
    }
}
