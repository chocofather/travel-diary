package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AttractionInfoTranslation {
    private Long id;
    private Long destinationId;
    private String languageCode;
    private String closedDays;
    private String openingHours;
    private String admissionFee;
    private String guide;
}
