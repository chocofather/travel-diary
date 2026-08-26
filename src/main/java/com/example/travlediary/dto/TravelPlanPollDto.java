package com.example.travlediary.dto;

import com.example.travlediary.model.TravelPlanPoll;
import com.example.travlediary.model.TravelPlanPollOption;

import java.util.List;

/**
 * 화면으로 나가는 투표 한 건.
 *
 * <p>계정 정보는 싣지 않는다. 방 안에서만 뜻이 있는 memberId 와 그 방의 표시 이름까지다.
 * 누가 무엇을 골랐는지·표 수는 이번 단계에 없다.
 *
 * @param createdAt 만든 시각(epoch millis). 표시 형식은 보는 사람의 브라우저가 정한다.
 */
public record TravelPlanPollDto(
        Long id,
        String title,
        String selectionType,
        Long createdByMemberId,
        String createdByDisplayName,
        Long createdAt,
        List<TravelPlanPollOptionDto> options) {

    /** 이름을 알 수 없는 경우. 실제로는 멤버 행을 지우지 않아 거의 나오지 않는다. */
    private static final String UNKNOWN_CREATOR = "알 수 없음";

    public static TravelPlanPollDto of(TravelPlanPoll poll, List<TravelPlanPollOption> options) {
        String displayName = poll.getCreatedByDisplayName();
        return new TravelPlanPollDto(
                poll.getId(),
                poll.getTitle(),
                poll.getSelectionType() == null ? null : poll.getSelectionType().name(),
                poll.getCreatedByMemberId(),
                displayName == null || displayName.isBlank() ? UNKNOWN_CREATOR : displayName,
                poll.getCreatedAt() == null ? null : poll.getCreatedAt().getTime(),
                options.stream().map(TravelPlanPollOptionDto::of).toList());
    }
}
