package com.example.travlediary.dto;

import com.example.travlediary.service.faq.FaqCategoryBadge;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class FaqListItemDto {
    private Long id;
    private String question;
    private String answer;
    private Long orderIndex;
    private boolean visible;
    private Long categoryId;
    private String categoryName;

    /**
     * 공개 화면 카테고리 뱃지의 표시 클래스.
     * categoryName 은 언어에 따라 바뀌므로 스타일은 이 값으로만 정한다.
     * 공개 목록에서 언어 대체 이전의 한국어 이름으로 채워지고, 그 전에는 중립 값이다.
     */
    private String categoryBadge = FaqCategoryBadge.DEFAULT;

    private Timestamp createdAt;
    private Timestamp updatedAt;
}
