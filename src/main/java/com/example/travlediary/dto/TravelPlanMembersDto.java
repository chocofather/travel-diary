package com.example.travlediary.dto;

import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanMember;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 참여자 명단만 담은 한 벌.
 *
 * <p>명단이 바뀌었다는 알림을 받은 화면이 이만큼만 다시 읽는다.
 * 사람 하나 들어왔다고 방의 일정 전체를 다시 읽지 않기 위한 것이다.
 * 담는 값은 상세({@link TravelPlanDetailDto})의 참여자 부분과 같다.
 */
@Data
@AllArgsConstructor
public class TravelPlanMembersDto {
    private TravelPlan plan;
    /** 이 방에서의 현재 사용자 참여 정보 */
    private TravelPlanMember currentMember;
    /** 지금 참여 중인 사람들. OWNER 가 먼저, 그 뒤는 참여한 순서. */
    private List<TravelPlanMemberDto> members;
    /** 내보내진 사람들. OWNER 만 보므로 그 밖의 사용자에게는 비어 있다. */
    private List<TravelPlanPastMemberDto> pastMembers;
    /** 방 정원. 초대 미리보기와 같은 값을 쓴다. */
    private int memberLimit;

    /** "참여자 N/8" 의 N. 목록을 이미 읽었으므로 COUNT 를 따로 내지 않는다. */
    public int getMemberCount() {
        return members == null ? 0 : members.size();
    }
}
