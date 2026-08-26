package com.example.travlediary.model;

/**
 * travel_plan_chat_messages.message_type.
 * DB 의 CHECK 제약이 이 두 값만 허용한다.
 *
 * <p>SYSTEM 은 "OO님이 참여했어요" 같은 알림 자리다. 이번 단계에서는 만들지 않는다.
 */
public enum TravelPlanChatMessageType {
    USER,
    SYSTEM
}
