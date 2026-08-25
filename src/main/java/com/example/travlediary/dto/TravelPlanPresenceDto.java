package com.example.travlediary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 방 하나의 접속 현황. WebSocket 으로 나가는 값이라 꼭 필요한 것만 담는다.
 *
 * <p>참여자 이름은 이미 화면이 들고 있으므로 방 안의 id 만 보낸다.
 * 계정 정보(이메일/아이디/닉네임)는 절대 담지 않는다.
 */
@Data
@AllArgsConstructor
public class TravelPlanPresenceDto {
    /** travel_plan_members.id 목록. 방 안에서만 뜻이 있는 값이다. */
    private List<Long> onlineMemberIds;
    /** 지금 접속 중인 사람 수. 참여자 총원(memberCount)과는 다른 값이다. */
    private int onlineCount;

    public static TravelPlanPresenceDto of(List<Long> onlineMemberIds) {
        return new TravelPlanPresenceDto(onlineMemberIds, onlineMemberIds.size());
    }
}
