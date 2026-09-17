package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DiaryCoverLibraryReportForm {
    private Long photoAssetId;
    private String reasonCode;
    private String description;
}
