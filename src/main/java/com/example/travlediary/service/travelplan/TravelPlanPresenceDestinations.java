package com.example.travlediary.service.travelplan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 접속 표시가 쓰는 STOMP 목적지.
 * 방마다 topic 을 따로 두어 다른 방의 접속 정보가 섞이지 않게 한다.
 */
public final class TravelPlanPresenceDestinations {

    /** 서버 -> 클라이언트. 방 하나당 하나씩이다. */
    private static final String TOPIC_FORMAT = "/topic/travel-plans/%d/presence";
    private static final Pattern TOPIC_PATTERN =
            Pattern.compile("^/topic/travel-plans/(\\d+)/presence$");
    /** 클라이언트 -> 서버. 접속했다고 알리는 하나뿐이다. */
    private static final Pattern JOIN_PATTERN =
            Pattern.compile("^/app/travel-plans/(\\d+)/presence/join$");

    private TravelPlanPresenceDestinations() {
    }

    public static String topic(Long travelPlanId) {
        return String.format(TOPIC_FORMAT, travelPlanId);
    }

    /**
     * 구독하려는 목적지에서 방 번호를 꺼낸다.
     *
     * @return 접속 표시 topic 이 아니면 null
     */
    public static Long travelPlanIdOf(String destination) {
        return travelPlanIdOf(destination, TOPIC_PATTERN);
    }

    /**
     * 접속했다고 알리는 SEND 목적지에서 방 번호를 꺼낸다.
     *
     * @return 그 목적지가 아니면 null
     */
    public static Long joinTravelPlanIdOf(String destination) {
        return travelPlanIdOf(destination, JOIN_PATTERN);
    }

    private static Long travelPlanIdOf(String destination, Pattern pattern) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(destination);
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
