package com.example.travlediary.dto;

import com.example.travlediary.model.TravelPlanChatReactionType;

/**
 * 한 메시지에 달린 반응 한 종류.
 *
 * <p>누가 눌렀는지는 싣지 않는다. 몇 명인지와, 보는 사람이 눌렀는지까지다.
 * 개수도 눌렀는지도 서버가 센 값이다. 화면이 더하거나 빼지 않는다.
 */
public record TravelPlanChatReactionDto(
        String type,
        String emoji,
        int count,
        boolean reacted) {

    public static TravelPlanChatReactionDto of(TravelPlanChatReactionType type,
                                               int count,
                                               boolean reacted) {
        return new TravelPlanChatReactionDto(type.name(), type.emoji(), count, reacted);
    }
}
