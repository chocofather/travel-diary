package com.example.travlediary.dto;

/**
 * 방 전체로 나가는 채팅 알림.
 *
 * <p>새 메시지는 내용을 함께 싣고, 지움은 어느 메시지인지만 알린다.
 * 화면은 그 한 건만 고쳐 그리므로 전체 목록을 다시 읽지 않는다.
 */
public record TravelPlanChatEventDto(
        String type,
        TravelPlanChatMessageDto message,
        Long messageId) {

    public static final String MESSAGE_CREATED = "MESSAGE_CREATED";
    public static final String MESSAGE_DELETED = "MESSAGE_DELETED";
    public static final String MESSAGE_REACTION_CHANGED = "MESSAGE_REACTION_CHANGED";

    public static TravelPlanChatEventDto created(TravelPlanChatMessageDto message) {
        return new TravelPlanChatEventDto(MESSAGE_CREATED, message, message.id());
    }

    public static TravelPlanChatEventDto deleted(Long messageId) {
        return new TravelPlanChatEventDto(MESSAGE_DELETED, null, messageId);
    }

    /**
     * 어느 메시지의 반응이 달라졌다는 것만 알린다.
     *
     * <p>개수를 싣지 않는다. 받은 쪽은 그 메시지의 요약을 서버에서 다시 읽는다.
     * 개수를 실어 보내면 같은 알림이 두 번 왔을 때 화면이 그것을 더해 어긋난다.
     */
    public static TravelPlanChatEventDto reactionChanged(Long messageId) {
        return new TravelPlanChatEventDto(MESSAGE_REACTION_CHANGED, null, messageId);
    }
}
