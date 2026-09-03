package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CountryCategoryTranslation {
    private Long id;
    private Long countryCategoryId;
    private String languageCode;
    private String name;
}
