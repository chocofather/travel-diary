package com.example.travlediary.dto;

/**
 * 방 전체로 나가는 투표 알림.
 *
 * <p>표가 바뀌었다는 알림에는 숫자를 싣지 않는다.
 * 화면이 스스로 +1 하지 않고 서버에서 지금 값을 다시 읽게 하려는 것이다.
 * DB 가 계속 기준이다.
 *
 * @param poll 만들어졌을 때만 채워진다. 채팅에 남길 알림에 쓴다
 */
public record TravelPlanPollEventDto(String type, TravelPlanPollDto poll, Long pollId) {

    public static final String POLL_CREATED = "POLL_CREATED";
    public static final String POLL_VOTED = "POLL_VOTED";

    public static TravelPlanPollEventDto created(TravelPlanPollDto poll) {
        return new TravelPlanPollEventDto(POLL_CREATED, poll, poll.id());
    }

    public static TravelPlanPollEventDto voted(Long pollId) {
        return new TravelPlanPollEventDto(POLL_VOTED, null, pollId);
    }
}
