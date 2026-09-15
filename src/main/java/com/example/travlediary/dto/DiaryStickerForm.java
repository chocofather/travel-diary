package com.example.travlediary.dto;

import com.example.travlediary.model.DiaryStickerAccessTier;
import com.example.travlediary.model.DiaryStickerType;
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
