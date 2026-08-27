package com.example.travlediary.dto;

import java.util.List;

/**
 * 투표 상세.
 *
 * <p>계정 정보는 싣지 않는다. 방 안에서만 뜻이 있는 표시 이름까지다.
 * 누가 무엇을 골랐는지도 싣지 않는다. 내 선택과 선택지별 합계까지만 나간다.
 *
 * <p>결과를 마감 뒤에 공개하는 투표라면 진행 중에는 표와 결과를 아예 담지 않는다.
 * 화면에서 가리는 것만으로는 값이 그대로 나간다.
 *
 * @param selectedOptionIds 지금 보고 있는 사람이 고른 선택지. 아직 투표하지 않았으면 빈 목록
 * @param resultsVisible    지금 표를 보여 줄 때인지. 아니면 각 선택지의 표와 결과가 비어 있다
 * @param closable          지금 보고 있는 사람이 이 투표를 직접 마감할 수 있는지
 *                          (만든 사람과 지금 방장. 끝난 투표에서는 false)
 * @param deletable         지금 보고 있는 사람이 이 투표를 지울 수 있는지
 *                          (마감과 같은 기준. 끝난 투표도 지울 수 있다)
 */
public record TravelPlanPollDetailDto(
        Long id,
        String title,
        String createdByDisplayName,
        String selectionType,
        String status,
        String closeType,
        Long deadlineAt,
        int votedMemberCount,
        int activeMemberCount,
        boolean resultsVisible,
        boolean closable,
        boolean deletable,
        String winnerSummary,
        List<TravelPlanPollOptionResultDto> options,
        List<Long> selectedOptionIds) {
}
