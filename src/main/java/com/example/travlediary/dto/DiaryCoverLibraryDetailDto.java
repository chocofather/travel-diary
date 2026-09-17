package com.example.travlediary.dto;

import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;

import java.util.List;

public record DiaryCoverLibraryDetailDto(
        DiaryCoverLibraryItem item,
        List<DiaryCoverLibraryElement> elements) {
}
