package com.example.travlediary.dto;

import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import com.example.travlediary.model.DiaryCoverLibraryReport;

import java.util.List;

public record DiaryCoverLibraryReportDetailDto(
        DiaryCoverLibraryReport report,
        DiaryCoverLibraryItem item,
        List<DiaryCoverLibraryElement> elements,
        DiaryCoverLibraryPhotoAsset targetPhotoAsset,
        String reporterDisplayName) {
}
