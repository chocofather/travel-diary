package com.example.travlediary.dto;

import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanDay;
import com.example.travlediary.model.TravelPlanItem;
import com.example.travlediary.model.TravelPlanItemAlternative;
import com.example.travlediary.model.TravelPlanMember;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 방 기본 상세. A 일정과 그에 붙은 대안(B/C)을 함께 담는다. */
@Data
@AllArgsConstructor
public class TravelPlanDetailDto {
    private TravelPlan plan;
    /** 이 방에서의 현재 사용자 참여 정보 */
    private TravelPlanMember currentMember;
    /** day_number 오름차순 */
    private List<TravelPlanDay> days;
    /** DAY id -> 그 DAY 의 A 일정(display_order 오름차순). 일정이 없는 DAY 는 키가 없다. */
    private Map<Long, List<TravelPlanItem>> itemsByDayId;
    /** A 일정 id -> 그 일정의 대안(alternative_order 오름차순). 대안이 없는 일정은 키가 없다. */
    private Map<Long, List<TravelPlanItemAlternative>> alternativesByItemId;
    /** 지금 참여 중인 사람들. OWNER 가 먼저, 그 뒤는 참여한 순서. */
    private List<TravelPlanMemberDto> members;
    /** 방 정원. 초대 미리보기와 같은 값을 쓴다. */
    private int memberLimit;

    /** "참여자 N/8" 의 N. 목록을 이미 읽었으므로 COUNT 를 따로 내지 않는다. */
    public int getMemberCount() {
        return members == null ? 0 : members.size();
    }
}
