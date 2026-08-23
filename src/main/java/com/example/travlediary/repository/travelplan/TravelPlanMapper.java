package com.example.travlediary.repository.travelplan;

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
}
