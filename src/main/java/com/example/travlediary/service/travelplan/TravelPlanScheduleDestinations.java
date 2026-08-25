package com.example.travlediary.service.travelplan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 일정 변경 알림이 쓰는 STOMP 목적지.
 * 접속 표시(presence)와 다른 topic 이고, 방마다 따로 둔다.
 */
public final class TravelPlanScheduleDestinations {

    /** 서버 -> 클라이언트. 방 하나당 하나씩이다. */
    private static final String TOPIC_FORMAT = "/topic/travel-plans/%d/schedule";
    private static final Pattern TOPIC_PATTERN =
            Pattern.compile("^/topic/travel-plans/(\\d+)/schedule$");

    private TravelPlanScheduleDestinations() {
    }

    public static String topic(Long travelPlanId) {
        return String.format(TOPIC_FORMAT, travelPlanId);
    }

    /**
     * 구독하려는 목적지에서 방 번호를 꺼낸다.
     *
     * @return 일정 변경 topic 이 아니면 null
     */
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
