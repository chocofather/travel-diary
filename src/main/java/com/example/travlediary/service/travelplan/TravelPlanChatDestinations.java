package com.example.travlediary.service.travelplan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 방 채팅이 오가는 STOMP 목적지.
 * 접속 표시·일정 변경·작성 중 상태와 다른 topic 이고, 방마다 따로 둔다.
 */
public final class TravelPlanChatDestinations {

    /** 서버 -> 방 전체 */
    private static final String TOPIC_FORMAT = "/topic/travel-plans/%d/chat";
    private static final Pattern TOPIC_PATTERN =
            Pattern.compile("^/topic/travel-plans/(\\d+)/chat$");

    /** 클라이언트 -> 서버. 보내기 / 지우기 / 읽음 세 가지뿐이다. */
    private static final Pattern SEND_PATTERN =
            Pattern.compile("^/app/travel-plans/(\\d+)/chat/(send|delete|read)$");

    /** 보낸 사람에게만 돌아가는 처리 결과. 실패 사유가 방 전체로 나가지 않게 한다. */
    public static final String REPLY_QUEUE = "/queue/travel-plan-chat";

    private TravelPlanChatDestinations() {
    }

    public static String topic(Long travelPlanId) {
        return String.format(TOPIC_FORMAT, travelPlanId);
    }

    /** @return 채팅 topic 이 아니면 null */
    public static Long travelPlanIdOf(String destination) {
        return travelPlanIdOf(destination, TOPIC_PATTERN);
    }

    /**
     * 보내려는 목적지에서 방 번호를 꺼낸다.
     * 여기 걸리지 않는 SEND 는 받지 않는다.
     *
     * @return 허용하지 않는 목적지면 null
     */
    public static Long sendTravelPlanIdOf(String destination) {
        return travelPlanIdOf(destination, SEND_PATTERN);
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
