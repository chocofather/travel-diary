package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DiaryCoverLibraryModerationForm {
    private String decision;
    private Long photoAssetId;
    private String reason;
    private String adminNote;
}
