package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 자주 묻는 질문 카테고리 이름의 언어별 값.
 *
 * <p>한국어 원문은 {@code faq_categories}.category_name 이 갖고,
 * 여기에는 en / ja / zh-CN / zh-TW 만 담는다. 번역 대상은 이름 하나뿐이다.
 */
@Data
@NoArgsConstructor
public class FaqCategoryTranslation {
    private Long id; // 자주묻는질문 카테고리 다국어 번호
    private Long faqCategoryId; // 자주묻는질문 카테고리 번호
    private String languageCode; // 언어 코드 예: 'en', 'ja'
    private String categoryName; // 다국어 카테고리 이름
}
