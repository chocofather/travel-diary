package com.tripbora.repository.diary;

import com.tripbora.dto.DiaryStickerFilter;
import com.tripbora.model.DiaryStickerCatalogItem;
import com.tripbora.model.DiaryStickerCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiaryStickerMapper {
    List<DiaryStickerCategoryEntity> findAllCategories();
    List<DiaryStickerCategoryEntity> findVisibleCategories();
    DiaryStickerCategoryEntity findCategoryById(Long id);
    int insertCategory(DiaryStickerCategoryEntity category);
    int updateCategory(DiaryStickerCategoryEntity category);

    List<DiaryStickerCatalogItem> findAllStickers();
    List<DiaryStickerCatalogItem> findAdminStickers(DiaryStickerFilter filter);
    List<DiaryStickerCatalogItem> findVisibleStickers();
    DiaryStickerCatalogItem findStickerById(Long id);
    DiaryStickerCatalogItem findVisibleByCatalogKey(String catalogKey);
    DiaryStickerCatalogItem findByImageUrl(String imageUrl);
    int insertSticker(DiaryStickerCatalogItem sticker);
    int updateSticker(DiaryStickerCatalogItem sticker);
    int hideSticker(Long id);
    int countStickersByCategoryId(Long categoryId);
    int countCategoryName(@Param("name") String name, @Param("excludeId") Long excludeId);
}
