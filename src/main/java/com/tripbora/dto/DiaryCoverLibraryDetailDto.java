package com.tripbora.dto;

import com.tripbora.model.DiaryCoverLibraryElement;
import com.tripbora.model.DiaryCoverLibraryItem;

import java.util.List;

public record DiaryCoverLibraryDetailDto(
        DiaryCoverLibraryItem item,
        List<DiaryCoverLibraryElement> elements) {
}
