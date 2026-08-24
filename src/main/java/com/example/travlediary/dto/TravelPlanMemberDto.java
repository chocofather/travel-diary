package com.example.travlediary.dto;

import com.example.travlediary.model.TravelPlanRole;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 플래너의 참여자 목록에 한 줄로 나가는 값.
 * 화면이 쓰는 것만 담아 users 의 계정 정보나 user_id 가 view 로 넘어가지 않게 한다.
 * 표시 이름의 기준은 언제나 travel_plan_members.display_name 이다.
 */
@Data
@AllArgsConstructor
public class TravelPlanMemberDto {
    /**
     * travel_plan_members.id. 내보내기 대상만 가리키는 값이고 users.id 가 아니다.
     * 서버는 이 값이 그 방 소속인지 다시 확인한다.
     */
    private Long memberId;
    private String displayName;
    private TravelPlanRole role;
    /** 지금 보고 있는 사람 자신인지. 화면에서 "(나)" 를 붙일 때만 쓴다. */
    private boolean currentUser;
}
