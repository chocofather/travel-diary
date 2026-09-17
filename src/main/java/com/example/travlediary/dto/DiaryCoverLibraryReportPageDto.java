package com.example.travlediary.dto;

import java.util.List;

public record DiaryCoverLibraryReportPageDto(
        List<DiaryCoverLibraryReportListItemDto> items,
        DiaryCoverLibraryReportStatusFilter filter,
        int currentPage,
        int totalPages,
        int totalCount,
        int pageSize) {

    public DiaryCoverLibraryReportStatusFilter[] getFilterOptions() {
        return DiaryCoverLibraryReportStatusFilter.values();
    }
}
