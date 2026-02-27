package com.example.travlediary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResult<T> {
    private List<T> content;       // 실제 데이터 목록 (댓글 등)
    private int totalElements;     // 전체 개수 (삭제 제외)
    private int currentPage;       // 현재 페이지
    private int pageSize;          // 페이지당 개수

    public boolean isLast() {
        return (currentPage + 1) * pageSize >= totalElements;
    }
}
