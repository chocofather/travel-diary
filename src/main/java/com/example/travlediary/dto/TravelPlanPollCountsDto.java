package com.example.travlediary.dto;

/**
 * 투표 센터 탭에 붙는 숫자.
 *
 * <p>목록을 열지 않아도 두 숫자가 맞아야 해서 따로 둔다.
 * 숫자를 알려고 지난 투표 목록 전체를 가져오지 않는다.
 */
public record TravelPlanPollCountsDto(int open, int closed) {
}
