package com.example.travlediary.dto;

/**
 * 상세 화면의 선택지 한 줄.
 *
 * @param voteCount 이 선택지를 고른 사람 수.
 *                  여러 개 선택 투표에서는 이 값들의 합이 참여 인원과 다를 수 있다.
 */
public record TravelPlanPollOptionResultDto(Long id, String content, int voteCount) {
}
