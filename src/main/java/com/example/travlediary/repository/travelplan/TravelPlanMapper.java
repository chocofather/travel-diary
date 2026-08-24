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

    /**
     * 참여 처리용 방 조회. 그 방 row 에 잠금을 걸어 동시 참여를 한 줄로 세운다.
     * 정원 계산과 INSERT 가 이 잠금 안에서 일어나야 8명을 넘길 수 없다.
     * 반드시 트랜잭션 안에서만 부른다.
     */
    TravelPlan findPlanByIdAndStatusForUpdate(@Param("travelPlanId") Long travelPlanId,
                                              @Param("planStatus") String planStatus);

    /**
     * 상태를 가리지 않는 참여 기록 1건.
     * LEFT / REMOVED 로 남아 있는 사람이 초대로 새 row 를 만들지 못하게 확인할 때 쓴다.
     */
    TravelPlanMember findAnyMemberByPlanAndUser(@Param("travelPlanId") Long travelPlanId,
                                                @Param("userId") Long userId);

    /**
     * 방 안에서 그 표시 이름을 이미 쓰고 있는 참여 기록 수.
     * 나갔던 사람이 쓰던 이름도 예약된 것으로 보아 새 참여자가 가져가지 못하게 한다
     * (uk_travel_plan_members_plan_display_name 과 같은 기준).
     */
    int countMembersByPlanAndDisplayName(@Param("travelPlanId") Long travelPlanId,
                                         @Param("displayName") String displayName);

    /** 방의 참여자 수. 초대 미리보기의 "N/8" 과 정원 검사에 쓴다. */
    int countMembersByPlanAndStatus(@Param("travelPlanId") Long travelPlanId,
                                    @Param("memberStatus") String memberStatus);

    /**
     * 방에서 그 역할을 맡고 있는 참여자 1건.
     * 초대 미리보기에서 OWNER 의 방 표시 이름을 읽을 때 쓴다(회원 개인정보는 읽지 않는다).
     */
    TravelPlanMember findMemberByPlanAndRole(@Param("travelPlanId") Long travelPlanId,
                                             @Param("role") String role,
                                             @Param("memberStatus") String memberStatus);

    /** 방의 DAY 목록. day_number 오름차순. */
    List<TravelPlanDay> findDaysByPlanId(@Param("travelPlanId") Long travelPlanId);

    /**
     * DAY 1건. 방 소속 조건을 함께 걸어 다른 방의 dayId 를 섞어 넣을 수 없게 한다.
     */
    TravelPlanDay findDayByPlanAndId(@Param("travelPlanId") Long travelPlanId,
                                     @Param("dayId") Long dayId);

    /**
     * 마지막 활동 시각 갱신. 목록 정렬 기준이라 실제 변경이 있을 때만 부른다.
     * (조회에서는 부르지 않는다)
     */
    int touchLastActivity(@Param("travelPlanId") Long travelPlanId);
}
