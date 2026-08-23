package com.example.travlediary.repository.travelplan;

import com.example.travlediary.dto.TravelPlanListItemDto;
import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanDay;
import com.example.travlediary.model.TravelPlanMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TravelPlanMapper {

    /** 방 1건 등록. useGeneratedKeys 로 plan.id 가 채워진다. */
    int insertPlan(TravelPlan plan);

    /** 참여자 1건 등록. 방 생성 시에는 OWNER 를 넣는다. */
    int insertMember(TravelPlanMember member);

    /**
     * 여행 기간의 DAY 를 한 번에 등록한다.
     *
     * @param days day_number / plan_date 를 담은 목록. travel_plan_id 는 별도 파라미터를 쓴다.
     */
    int insertDays(@Param("travelPlanId") Long travelPlanId,
                   @Param("days") List<TravelPlanDay> days);

    /**
     * 현재 사용자가 참여 중인 방 목록. 정렬은 최근 활동 순.
     * 상태 값은 SQL 에 박지 않고 enum 이름을 그대로 넘긴다.
     */
    List<TravelPlanListItemDto> findActivePlansByUserId(@Param("userId") Long userId,
                                                        @Param("planStatus") String planStatus,
                                                        @Param("memberStatus") String memberStatus);

    /** 방 1건. 상태 조건을 함께 걸어 ACTIVE 가 아닌 방이 새어 나가지 않게 한다. */
    TravelPlan findPlanByIdAndStatus(@Param("travelPlanId") Long travelPlanId,
                                     @Param("planStatus") String planStatus);

    /** 이 방에서의 현재 사용자 참여 정보. 없으면 접근 권한이 없다는 뜻이다. */
    TravelPlanMember findMemberByPlanAndUser(@Param("travelPlanId") Long travelPlanId,
                                             @Param("userId") Long userId,
                                             @Param("memberStatus") String memberStatus);

    /** 방의 DAY 목록. day_number 오름차순. */
    List<TravelPlanDay> findDaysByPlanId(@Param("travelPlanId") Long travelPlanId);
}
