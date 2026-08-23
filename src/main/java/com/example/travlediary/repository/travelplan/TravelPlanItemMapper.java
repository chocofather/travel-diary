package com.example.travlediary.repository.travelplan;

import com.example.travlediary.model.TravelPlanItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TravelPlanItemMapper {

    /** DAY 의 A 일정 목록. display_order, id 오름차순. */
    List<TravelPlanItem> findByDayId(@Param("travelPlanDayId") Long travelPlanDayId);

    /**
     * 방 전체의 A 일정. 전체 상세 화면에서 DAY 수만큼 조회가 나가지 않도록 한 번에 읽는다.
     * 호출자가 travel_plan_day_id 로 묶어서 쓴다.
     */
    List<TravelPlanItem> findByPlanId(@Param("travelPlanId") Long travelPlanId);

    /** DAY 의 마지막 순서. 일정이 없으면 0 을 돌려준다. */
    int findMaxDisplayOrder(@Param("travelPlanDayId") Long travelPlanDayId);

    /** A 일정 1건 등록. version 등 DB DEFAULT 는 그대로 둔다. */
    int insertItem(TravelPlanItem item);
}
