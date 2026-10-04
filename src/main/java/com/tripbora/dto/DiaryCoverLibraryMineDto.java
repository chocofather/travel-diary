package com.tripbora.dto;

import com.tripbora.model.DiaryCoverLibraryElement;
import com.tripbora.model.DiaryCoverLibraryItem;

import java.util.List;
import java.util.Map;

public record DiaryCoverLibraryMineDto(
        List<DiaryCoverLibraryItem> items,
        Map<Long, List<DiaryCoverLibraryElement>> elementsByItem) {
}
