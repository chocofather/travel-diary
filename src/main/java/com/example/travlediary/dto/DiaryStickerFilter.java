package com.example.travlediary.dto;

import com.example.travlediary.model.DiaryStickerAccessTier;
import com.example.travlediary.model.DiaryStickerType;
import lombok.Data;

@Data
public class DiaryStickerFilter {
    private Long categoryId;
    private DiaryStickerType stickerType;
    private DiaryStickerAccessTier accessTier;
    private Boolean visible;
}
