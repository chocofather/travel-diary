package com.example.travlediary.service.travelplan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 방 투표가 오가는 STOMP 목적지.
 * 접속 표시·일정 변경·작성 중 상태·채팅과 다른 topic 이고, 방마다 따로 둔다.
 *
 * <p>보내는 쪽은 없다. 투표 만들기는 기존 HTTP POST 로 저장한다.
 */
public final class TravelPlanPollDestinations {

    /** 서버 -> 방 전체 */
    private static final String TOPIC_FORMAT = "/topic/travel-plans/%d/polls";
    private static final Pattern TOPIC_PATTERN =
            Pattern.compile("^/topic/travel-plans/(\\d+)/polls$");

    private TravelPlanPollDestinations() {
    }

    public static String topic(Long travelPlanId) {
        return String.format(TOPIC_FORMAT, travelPlanId);
    }

    /** @return 투표 topic 이 아니면 null */
    public static Long travelPlanIdOf(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
