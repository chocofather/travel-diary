package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DiaryCoverLibraryRestoreForm {
    private String reason;
    private String adminNote;
}
