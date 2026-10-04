package com.tripbora.dto;

import com.tripbora.model.DiaryStickerAccessTier;
import com.tripbora.model.DiaryStickerType;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class DiaryStickerForm {
    private String name;
    private Long categoryId;
    private DiaryStickerType stickerType = DiaryStickerType.NORMAL;
    private DiaryStickerAccessTier accessTier = DiaryStickerAccessTier.FREE;
    private Boolean visible = true;
    private Integer displayOrder = 1;
    private MultipartFile image;
}
