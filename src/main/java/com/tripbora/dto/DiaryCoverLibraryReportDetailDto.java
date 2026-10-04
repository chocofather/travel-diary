package com.tripbora.dto;

import com.tripbora.model.DiaryCoverLibraryElement;
import com.tripbora.model.DiaryCoverLibraryItem;
import com.tripbora.model.DiaryCoverLibraryPhotoAsset;
import com.tripbora.model.DiaryCoverLibraryReport;

import java.util.List;

public record DiaryCoverLibraryReportDetailDto(
        DiaryCoverLibraryReport report,
        DiaryCoverLibraryItem item,
        List<DiaryCoverLibraryElement> elements,
        DiaryCoverLibraryPhotoAsset targetPhotoAsset,
        String reporterDisplayName) {
}
