package com.tripbora.dto;

import com.tripbora.model.DiaryCoverLibraryPhotoShareMode;

public record DiaryCoverLibraryPhotoSelection(
        DiaryCoverLibraryPhotoShareMode mode,
        boolean rightsConfirmed) {
}
