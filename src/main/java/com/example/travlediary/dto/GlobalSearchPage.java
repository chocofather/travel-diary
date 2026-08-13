package com.example.travlediary.dto;

import java.util.List;

public record GlobalSearchPage(
        String query,
        String type,
        List<GlobalSearchResultDto> results,
        long totalCount,
        int currentPage,
        int pageSize,
        int totalPages,
        int pageStart,
        int pageEnd
) {

    public boolean hasQuery() {
        return query != null;
    }
}
