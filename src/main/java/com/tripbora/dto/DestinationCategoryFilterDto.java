package com.tripbora.dto;

/**
 * 공개 여행지 목록 카테고리 필터 한 칸.
 *
 * @param id       카테고리 번호. 목록 주소의 category 값이다.
 * @param name     요청 언어로 바꾼 표시 이름 (번역이 없으면 한국어 원문)
 * @param count    지금 지역 범위에서 이 카테고리가 등록된 여행지 수
 * @param selected 지금 고른 카테고리인지
 */
public record DestinationCategoryFilterDto(Long id, String name, int count, boolean selected) {
}
