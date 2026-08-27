package com.example.travlediary.model;

/**
 * 메시지에 남길 수 있는 반응.
 *
 * <p>여기 있는 여섯 가지가 전부다. 화면이 보낸 값은 반드시 {@link #from(String)} 을 거치므로
 * 목록에 없는 것은 서버에서 걸린다. DB 의 CHECK 제약
 * (chk_travel_plan_chat_message_reactions_type)과 같은 이름을 쓴다.
 *
 * <p>DB 에는 이모지 글자가 아니라 이 이름을 넣는다.
 * ❤️ 처럼 variation selector 가 붙는 글자는 보내는 쪽에 따라 값이 달라져,
 * 그대로 저장하면 같은 반응이 다른 행으로 들어가 UNIQUE 가 막지 못한다.
 */
public enum TravelPlanChatReactionType {
    LIKE("👍"),
    HEART("❤️"),
    LAUGH("😂"),
    WOW("😮"),
    SAD("😢"),
    PARTY("🎉");

    private final String emoji;

    TravelPlanChatReactionType(String emoji) {
        this.emoji = emoji;
    }

    /** 화면에 그리는 글자. 저장하는 값이 아니다. */
    public String emoji() {
        return emoji;
    }

    /**
     * 화면에서 받은 값. 아는 이름일 때만 통과한다.
     *
     * @return 모르는 값이면 null
     */
    public static TravelPlanChatReactionType from(String value) {
        if (value == null) {
            return null;
        }
        String name = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (TravelPlanChatReactionType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }
}
