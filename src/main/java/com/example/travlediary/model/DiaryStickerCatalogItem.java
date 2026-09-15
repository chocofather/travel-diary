package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DiaryStickerCatalogItem {
    private Long id;
    private String catalogKey;
    private String name;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private String imageUrl;
    private DiaryStickerType stickerType;
    private DiaryStickerAccessTier accessTier;
    private boolean visible;
    private Integer displayOrder;
    private String collectionCode;
    private String tapeStyle;
    private String repeatLeftUrl;
    private String repeatCenterUrl;
    private String repeatRightUrl;

    public boolean hasRepeatImages() {
        return repeatLeftUrl != null && !repeatLeftUrl.isBlank()
                && repeatCenterUrl != null && !repeatCenterUrl.isBlank()
                && repeatRightUrl != null && !repeatRightUrl.isBlank();
    }
}
