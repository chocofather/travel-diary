package com.example.travlediary.dto;

import lombok.Data;

/** 한 카테고리에 등록된 여행지 수(목록 카테고리 필터의 선택지). */
@Data
public class CategoryDestinationCount {
    private Long categoryId;
    private int destinationCount;
}
