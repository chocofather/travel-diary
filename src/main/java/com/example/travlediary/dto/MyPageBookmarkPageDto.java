package com.example.travlediary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MyPageBookmarkPageDto {
    private final List<?> bookmarks;
    private final String section;
    private final String scope;
    private final String type;
    private final int currentPage;
    private final int totalPages;
    private final int totalCount;
}
