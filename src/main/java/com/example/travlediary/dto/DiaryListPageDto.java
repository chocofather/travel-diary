package com.example.travlediary.dto;

import java.util.List;

/**
 * 일기장형 목록 한 쪽. 카드 데이터는 기존 {@link DiaryListItemDto} 를 그대로 쓰고,
 * 검색어와 쪽 정보만 함께 담는다.
 */
public record DiaryListPageDto(List<DiaryListItemDto> items,
                               String keyword,
                               int currentPage,
                               int totalPages,
                               int totalCount,
                               int pageSize) {

    /** 검색 중인지. (결과가 없을 때 빈 다이어리 상태와 구분하는 데 쓴다) */
    public boolean isSearching() {
        return keyword != null && !keyword.isEmpty();
    }
}
