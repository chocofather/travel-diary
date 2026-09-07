package com.example.travlediary.dto;

import lombok.Data;

/**
 * 관리자 FAQ 카테고리 목록 한 줄.
 * 사용 중인 FAQ 수를 함께 읽어 삭제 가능 여부를 화면에서 바로 알 수 있게 한다.
 */
@Data
public class FaqCategoryListItemDto {
    private Long id;
    private String categoryName;
    private int faqCount;
}
