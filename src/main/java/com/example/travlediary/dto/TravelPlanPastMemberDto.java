package com.example.travlediary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * OWNER 의 "이전 참여자" 목록에 한 줄로 나가는 값.
 * 지금은 내보내진 사람만 담는다(스스로 나간 사람은 본인이 링크로 돌아올 수 있어 관리가 필요 없다).
 * 화면이 쓰는 것만 담아 user_id 나 계정 정보가 view 로 넘어가지 않게 한다.
 */
@Data
@AllArgsConstructor
public class TravelPlanPastMemberDto {
    /** travel_plan_members.id. 재참여 허용 대상만 가리키는 값이고 users.id 가 아니다. */
    private Long memberId;
    private String displayName;
    /** 이미 재참여를 허용해 둔 사람인지. 참이면 허용 버튼을 다시 보여 주지 않는다. */
    private boolean rejoinAllowed;
}
