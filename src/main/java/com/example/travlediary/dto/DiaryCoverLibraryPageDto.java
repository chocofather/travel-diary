package com.example.travlediary.dto;

import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;

import java.util.List;
import java.util.Map;

public record DiaryCoverLibraryPageDto(
        List<DiaryCoverLibraryItem> items,
        Map<Long, List<DiaryCoverLibraryElement>> elementsByItem,
        DiaryCoverLibrarySort sort,
        int currentPage,
        int totalPages,
        int totalCount,
        int pageSize) {

    public String sortParam() {
        return sort == null || sort == DiaryCoverLibrarySort.DEFAULT ? null : sort.name();
    }

    public List<DiaryCoverLibrarySort> sortOptions() {
        return DiaryCoverLibrarySort.options();
    }

    public String sortParamFor(DiaryCoverLibrarySort option) {
        return option == null || option == DiaryCoverLibrarySort.DEFAULT ? null : option.name();
    }
}
