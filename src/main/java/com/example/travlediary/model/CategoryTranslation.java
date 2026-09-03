package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CategoryTranslation {
    private Long id;
    private Long categoryId;
    private String languageCode;
    private String name;
}
