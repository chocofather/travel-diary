package com.example.travlediary.dto;

import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanDay;
import com.example.travlediary.model.TravelPlanMember;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 방 기본 상세. 일정(travel_plan_items)은 아직 담지 않는다. */
@Data
@AllArgsConstructor
public class TravelPlanDetailDto {
    private TravelPlan plan;
    /** 이 방에서의 현재 사용자 참여 정보 */
    private TravelPlanMember currentMember;
    /** day_number 오름차순 */
    private List<TravelPlanDay> days;
}
