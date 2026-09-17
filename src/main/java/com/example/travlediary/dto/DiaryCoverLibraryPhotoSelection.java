package com.example.travlediary.dto;

import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;

public record DiaryCoverLibraryPhotoSelection(
        DiaryCoverLibraryPhotoShareMode mode,
        boolean rightsConfirmed) {
}
