package com.example.travlediary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DestinationTranslationForm {
    private String languageCode;
    private String name;
    private String description;
    private String shortDescription;
}
