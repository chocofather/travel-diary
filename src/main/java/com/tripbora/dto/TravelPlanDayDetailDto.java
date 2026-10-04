package com.tripbora.dto;

import com.tripbora.model.TravelPlan;
import com.tripbora.model.TravelPlanDay;
import com.tripbora.model.TravelPlanItem;
import com.tripbora.model.TravelPlanMember;
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
