package com.example.travlediary.dto;

import java.util.Map;

public record DiaryCoverLibraryRegistrationRequest(
        Long sourceCoverDesignId,
        String title,
        String description,
        Map<Long, DiaryCoverLibraryPhotoSelection> photoSelections) {
}
