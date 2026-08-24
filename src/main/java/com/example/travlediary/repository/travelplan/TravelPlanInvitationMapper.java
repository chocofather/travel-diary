package com.example.travlediary.repository.travelplan;

import com.example.travlediary.model.TravelPlanInvitation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 초대 링크 전용 매퍼.
 * 방/참여자 조회는 TravelPlanMapper 를 그대로 쓰고, 여기서는
 * travel_plan_invitations 만 다룬다.
 */
@Mapper
public interface TravelPlanInvitationMapper {

    /** 방에 살아 있는 초대 링크 1건. 없으면 null. */
    TravelPlanInvitation findActiveByPlanId(@Param("travelPlanId") Long travelPlanId,
                                            @Param("status") String status);

    /** 초대 링크 1건 등록. status 는 enum 이름을 그대로 저장한다. */
    int insertInvitation(TravelPlanInvitation invitation);

    /**
     * 방의 ACTIVE 링크를 끈다. 재발급이면 REPLACED, OWNER 가 직접 끄면 DISABLED 를 넘긴다.
     * 상태를 바꾸는 순간 invalidated_at 이 찍혀 그 링크는 다시 조회되지 않는다.
     *
     * @return 실제로 꺼진 건수. 0 이면 살아 있는 링크가 없었다는 뜻이다.
     */
    int invalidateActiveInvitation(@Param("travelPlanId") Long travelPlanId,
                                   @Param("fromStatus") String fromStatus,
                                   @Param("toStatus") String toStatus);

    /**
     * 초대 링크로 들어온 요청을 해석한다.
     * raw token 은 저장하지 않으므로 SHA-256 hex 로만 찾는다.
     * REPLACED / DISABLED 는 상태 조건에서 걸려 조회되지 않는다.
     */
    TravelPlanInvitation findActiveByTokenHash(@Param("tokenHash") String tokenHash,
                                               @Param("status") String status);
}
