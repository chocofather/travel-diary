package com.example.travlediary.service.travelplan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 작성 중 상태(잠금 / 임시 내용)가 오가는 STOMP 목적지.
 * 접속 표시·일정 변경과 다른 topic 이고, 방마다 따로 둔다.
 */
public final class TravelPlanEditorDestinations {

    /** 서버 -> 방 전체 */
    private static final String TOPIC_FORMAT = "/topic/travel-plans/%d/editor";
    private static final Pattern TOPIC_PATTERN =
            Pattern.compile("^/topic/travel-plans/(\\d+)/editor$");

    /** 클라이언트 -> 서버. lock / draft / unlock / sync 네 가지뿐이다. */
    private static final Pattern SEND_PATTERN =
            Pattern.compile("^/app/travel-plans/(\\d+)/editor/(lock|draft|unlock|sync)$");

    /** 요청한 사람에게만 돌아가는 잠금 결과. */
    public static final String LOCK_REPLY_QUEUE = "/queue/travel-plan-editor";

    private TravelPlanEditorDestinations() {
    }

    public static String topic(Long travelPlanId) {
        return String.format(TOPIC_FORMAT, travelPlanId);
    }

    /** @return 작성 중 상태 topic 이 아니면 null */
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
