package com.example.travlediary.dto;

import lombok.Data;

/**
 * 초대 링크로 들어오는 사람이 채우는 값.
 * 방과 초대는 URL 의 raw token 으로 찾고 userId / role 은 서버가 정하므로
 * 이 폼에는 이 방에서 쓸 이름 하나만 있다.
 */
@Data
public class TravelPlanJoinForm {
    /** travel_plan_members.display_name. 사이트 nickname 과는 별개다. */
    private String displayName;
}
