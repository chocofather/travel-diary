package com.example.travlediary.dto;

import java.util.List;

/**
 * 투표 상세.
 *
 * <p>계정 정보는 싣지 않는다. 방 안에서만 뜻이 있는 표시 이름까지다.
 * 누가 무엇을 골랐는지도 싣지 않는다. 내 선택과 선택지별 합계까지만 나간다.
 *
 * @param selectedOptionIds 지금 보고 있는 사람이 고른 선택지. 아직 투표하지 않았으면 빈 목록
 */
public record TravelPlanPollDetailDto(
        Long id,
        String title,
        String createdByDisplayName,
        String selectionType,
        String status,
        int votedMemberCount,
        int activeMemberCount,
        String winnerSummary,
        List<TravelPlanPollOptionResultDto> options,
        List<Long> selectedOptionIds) {
}
