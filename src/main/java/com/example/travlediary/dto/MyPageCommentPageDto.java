package com.example.travlediary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MyPageCommentPageDto {
    private final List<MyPageCommentDto> comments;
    private final String type;
    private final int currentPage;
    private final int totalPages;
    private final int totalCount;
}
