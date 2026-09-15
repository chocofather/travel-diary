package com.example.travlediary.dto;

import lombok.Data;

@Data
public class DiaryStickerCategoryForm {
    private String name;
    private Integer displayOrder = 1;
    private Boolean visible = true;
}
