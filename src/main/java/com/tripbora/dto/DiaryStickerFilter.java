package com.tripbora.dto;

import com.tripbora.model.DiaryStickerAccessTier;
import com.tripbora.model.DiaryStickerType;
import lombok.Data;

@Data
public class DiaryStickerFilter {
    private Long categoryId;
    private DiaryStickerType stickerType;
    private DiaryStickerAccessTier accessTier;
    private Boolean visible;
}
