package com.example.travlediary.dto;

import java.util.List;

/**
 * 채팅창 한 페이지. 오래된 것이 앞에 온다.
 *
 * <p>대화와 투표 알림은 표가 달라 하나의 번호로 자를 수 없다.
 * 그래서 각자의 마지막 번호를 함께 돌려주고, 다음 페이지는 그 둘을 기준으로 이어 간다.
 * 한쪽만 보고 자르면 다른 쪽이 영영 빠지기 때문이다.
 *
 * @param nextBeforeMessageId 다음에 요청할 대화 기준. 더 없으면 null
 * @param nextBeforePollId    다음에 요청할 투표 기준. 더 없으면 null
 */
public record TravelPlanChatTimelineDto(
        List<TravelPlanChatTimelineItemDto> items,
        boolean hasMore,
        Long nextBeforeMessageId,
        Long nextBeforePollId) {
}
