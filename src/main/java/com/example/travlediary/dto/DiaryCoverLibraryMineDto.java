package com.example.travlediary.dto;

import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;

import java.util.List;
import java.util.Map;

public record DiaryCoverLibraryMineDto(
        List<DiaryCoverLibraryItem> items,
        Map<Long, List<DiaryCoverLibraryElement>> elementsByItem) {
}
