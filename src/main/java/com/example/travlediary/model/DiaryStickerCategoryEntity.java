package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DiaryStickerCategoryEntity {
    private Long id;
    private String code;
    private String name;
    private Integer displayOrder;
    private boolean visible;
}
