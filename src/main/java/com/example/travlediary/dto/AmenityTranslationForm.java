package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AmenityTranslationForm {
    private Integer amenityId;
    private String languageCode;
    private String name;
}
