package com.example.travlediary.dto;

import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanDay;
import com.example.travlediary.model.TravelPlanItem;
import com.example.travlediary.model.TravelPlanMember;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** DAY 편집 화면 한 벌. Plan B/C 는 아직 담지 않는다. */
@Data
@AllArgsConstructor
public class TravelPlanDayDetailDto {
    private TravelPlan plan;
    /** 이 방에서의 현재 사용자 참여 정보 */
    private TravelPlanMember currentMember;
    private TravelPlanDay day;
    /** display_order 오름차순 A 일정 */
    private List<TravelPlanItem> items;
}
