package com.example.travlediary.dto;

/**
 * 투표 센터 목록의 한 줄.
 *
 * <p>목록에서는 선택지를 펼치지 않는다. 무엇을 정하는지와 얼마나 참여했는지까지다.
 * 선택지와 표 수는 카드를 눌러 상세로 들어갔을 때 읽는다.
 *
 * @param votedMemberCount 진행 중일 때 쓰는 참여 인원(지금 방에 남아 있는 사람 기준)
 * @param winnerSummary    끝난 투표에서만 채워지는 결과 요약. 진행 중이면 null
 */
public record TravelPlanPollSummaryDto(
        Long id,
        String title,
        String createdByDisplayName,
        String status,
        int votedMemberCount,
        int activeMemberCount,
        String winnerSummary) {
}
