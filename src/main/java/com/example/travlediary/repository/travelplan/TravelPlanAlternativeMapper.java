package com.example.travlediary.repository.travelplan;

import com.example.travlediary.model.TravelPlanItemAlternative;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * A 일정에 붙는 대안(B/C) 전용 매퍼.
 * A 일정 자체는 TravelPlanItemMapper 가 맡고, 여기서는 travel_plan_item_alternatives 만 다룬다.
 */
@Mapper
public interface TravelPlanAlternativeMapper {

    /** 한 A 일정의 대안 목록. alternative_order 오름차순(B, C). */
    List<TravelPlanItemAlternative> findByItemId(@Param("travelPlanItemId") Long travelPlanItemId);

    /**
     * 방 전체의 대안. 플래너 화면에서 일정 수만큼 조회가 나가지 않도록 한 번에 읽는다.
     * 호출자가 travel_plan_item_id 로 묶어서 쓴다.
     */
    List<TravelPlanItemAlternative> findByPlanId(@Param("travelPlanId") Long travelPlanId);

    /** 현재 대안 개수. 최대 2건 정책을 서버에서 확인할 때 쓴다. */
    int countByItemId(@Param("travelPlanItemId") Long travelPlanItemId);

    /** 대안 1건. A 일정 소속 조건을 함께 걸어 다른 일정의 대안을 섞을 수 없게 한다. */
    TravelPlanItemAlternative findByIdAndItemId(@Param("id") Long id,
                                                @Param("travelPlanItemId") Long travelPlanItemId);

    /** 자리(B/C)로 찾는다. 승격 대상 대안을 집을 때 쓴다. 없으면 null. */
    TravelPlanItemAlternative findByItemIdAndOrder(
            @Param("travelPlanItemId") Long travelPlanItemId,
            @Param("alternativeOrder") Integer alternativeOrder);

    /** 대안 1건 등록. version 등 DB DEFAULT 는 그대로 둔다. */
    int insertAlternative(TravelPlanItemAlternative alternative);

    /**
     * 조건/내용 수정. A 일정과 같은 낙관적 잠금이라 넘겨받은 version 이 그대로일 때만 반영된다.
     *
     * @return 1 이면 반영, 0 이면 그 사이 다른 변경이 있었거나 소속이 맞지 않는다.
     */
    int updateWithVersion(@Param("id") Long id,
                          @Param("travelPlanItemId") Long travelPlanItemId,
                          @Param("conditionLabel") String conditionLabel,
                          @Param("content") String content,
                          @Param("version") Integer version);

    /** 대안 1건 삭제. A 일정 소속까지 조건으로 건다. */
    int deleteByIdAndItemId(@Param("id") Long id,
                            @Param("travelPlanItemId") Long travelPlanItemId);

    /**
     * 남은 대안의 자리를 옮긴다(C 2 -> B 1).
     * 내용/조건/작성자는 그대로 두고 alternative_order 만 바꾼다.
     */
    int updateOrderByIdAndItemId(@Param("id") Long id,
                                 @Param("travelPlanItemId") Long travelPlanItemId,
                                 @Param("alternativeOrder") Integer alternativeOrder);
}
