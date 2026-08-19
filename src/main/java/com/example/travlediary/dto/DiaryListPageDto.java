package com.example.travlediary.dto;

import java.util.List;

/**
 * 일기장형 목록 한 쪽. 카드 데이터는 기존 {@link DiaryListItemDto} 를 그대로 쓰고,
 * 검색어와 쪽 정보만 함께 담는다.
 */
public record DiaryListPageDto(List<DiaryListItemDto> items,
                               String keyword,
                               DiarySort sort,
                               int currentPage,
                               int totalPages,
                               int totalCount,
                               int pageSize) {

    /** 검색 중인지. (결과가 없을 때 빈 다이어리 상태와 구분하는 데 쓴다) */
    public boolean isSearching() {
        return keyword != null && !keyword.isEmpty();
    }

    /** 주소에 남길 정렬값. 기본 정렬은 주소를 깔끔하게 두려고 생략한다. */
    public String sortParam() {
        return sort == null || sort == DiarySort.DEFAULT ? null : sort.name();
    }

    /** 정렬 고르기 목록 */
    public List<DiarySort> sortOptions() {
        return DiarySort.options();
    }

    /** 화면이 '생략해도 되는 값'을 알 수 있게 기본 정렬 이름을 함께 준다. */
    public String defaultSortName() {
        return DiarySort.DEFAULT.name();
    }
}
