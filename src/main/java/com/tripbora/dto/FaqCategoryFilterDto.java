package com.tripbora.dto;

/**
 * 공개 FAQ 카테고리 필터 한 칸.
 *
 * @param id   카테고리 번호. 필터 링크의 category 값이다.
 * @param name 요청 언어로 바꾼 표시 이름 (번역이 없으면 한국어 원문)
 */
public record FaqCategoryFilterDto(Long id, String name) {
}
