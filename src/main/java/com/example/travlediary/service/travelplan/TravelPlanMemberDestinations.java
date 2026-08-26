package com.example.travlediary.service.travelplan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 참여자 명단 변경이 쓰는 STOMP 목적지.
 *
 * <p>접속 표시(presence)와는 다른 topic 을 쓴다.
 * "접속 중 N명" 은 지금 창을 열어 둔 사람 수고,
 * 여기로 오는 "참여자 N/8" 은 방에 속한 사람 수라 서로 다른 값이다.
 *
 * <p>서버에서 방으로 알리기만 하므로 보내는 쪽 목적지는 없다.
 */
public final class TravelPlanMemberDestinations {

    /** 서버 -> 클라이언트. 방 하나당 하나씩이다. */
    private static final String TOPIC_FORMAT = "/topic/travel-plans/%d/members";
    private static final Pattern TOPIC_PATTERN =
            Pattern.compile("^/topic/travel-plans/(\\d+)/members$");

    private TravelPlanMemberDestinations() {
    }

    public static String topic(Long travelPlanId) {
        return String.format(TOPIC_FORMAT, travelPlanId);
    }

    /**
     * 구독하려는 목적지에서 방 번호를 꺼낸다.
     *
     * @return 참여자 명단 topic 이 아니면 null
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
