package com.example.travlediary.repository.travelplan;

import com.example.travlediary.dto.TravelPlanFinalListItemDto;
import com.example.travlediary.model.TravelPlanFinalDay;
import com.example.travlediary.model.TravelPlanFinalItem;
import com.example.travlediary.model.TravelPlanFinalItemAlternative;
import com.example.travlediary.model.TravelPlanFinalMember;
import com.example.travlediary.model.TravelPlanFinalSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 완료된 여행 계획의 최종본 전용 매퍼.
 *
 * <p>원본 표(travel_plan_days / items / alternatives)의 번호를 그대로 쓰지 않는다.
 * 최종본은 그 자체로 완결된 한 벌이라, 새로 만든 최종본 번호끼리만 이어 붙인다.
 * 원본이 나중에 어떻게 되든 최종본은 그대로 남는다.
 */
@Mapper
public interface TravelPlanFinalMapper {

    /**
     * 최종본의 머리 1건.
     * (travel_plan_id) 가 UNIQUE 라 한 방에 두 번 만들면 DB 가 막는다.
     */
    int insertSnapshot(TravelPlanFinalSnapshot snapshot);

    /** 완료 시점에 함께한 사람 1명. */
    int insertMember(TravelPlanFinalMember member);

    /** 완료된 계획의 하루 1건. */
    int insertDay(TravelPlanFinalDay day);

    /** 완료된 계획의 일정 한 줄. */
    int insertItem(TravelPlanFinalItem item);

    /** 완료된 계획의 대안(B/C) 1건. */
    int insertAlternative(TravelPlanFinalItemAlternative alternative);

    /** 이 방의 최종본이 이미 있는지. 두 번 만들지 않기 위한 확인이다. */
    boolean existsByPlanId(@Param("travelPlanId") Long travelPlanId);

    /**
     * 완료 시점에 이 사람이 그 여행에 있었는지.
     * 완료된 방으로 들어왔을 때 무엇을 안내할지 가르는 데 쓴다.
     */
    boolean existsMemberByPlanAndUser(@Param("travelPlanId") Long travelPlanId,
                                      @Param("userId") Long userId);

    // ── 완료된 여행 읽기 ────────────────────────────────────
    // 최종본은 한번 만들어지면 바뀌지 않는다. 여기서는 읽기만 한다.
    // 원본 일정(travel_plan_items 등)을 다시 조합하지 않는다.

    /**
     * 이 사람이 함께했던 완료된 여행 목록. 최근에 끝난 것부터 온다.
     * 참여 인원까지 한 번에 세어 목록에서 다시 조회하지 않게 한다.
     */
    List<TravelPlanFinalListItemDto> findSnapshotsByUserId(@Param("userId") Long userId);

    /**
     * 최종본 1건. 방 번호로 찾는다((travel_plan_id) 가 UNIQUE 라 하나뿐이다).
     * 그 여행에 함께했던 사람만 볼 수 있으므로 user_id 조건을 함께 건다.
     *
     * @return 그 사람의 최종본이 아니면 null
     */
    TravelPlanFinalSnapshot findSnapshotByPlanAndUser(@Param("travelPlanId") Long travelPlanId,
                                                      @Param("userId") Long userId);

    /** 완료 시점의 참여자. 방장이 먼저 오고 그 뒤는 이름순이다. */
    List<TravelPlanFinalMember> findMembersBySnapshotId(@Param("snapshotId") Long snapshotId);

    /** 완료된 계획의 날짜들. 순서대로 온다. */
    List<TravelPlanFinalDay> findDaysBySnapshotId(@Param("snapshotId") Long snapshotId);

    /**
     * 완료된 계획의 일정 전부. 날짜 수만큼 조회가 나가지 않게 한 번에 읽는다.
     * 호출한 쪽이 final_day_id 로 묶어서 쓴다.
     */
    List<TravelPlanFinalItem> findItemsBySnapshotId(@Param("snapshotId") Long snapshotId);

    /**
     * 완료된 계획의 대안(B/C) 전부. 일정 수만큼 조회가 나가지 않게 한 번에 읽는다.
     * 호출한 쪽이 final_item_id 로 묶어서 쓴다.
     */
    List<TravelPlanFinalItemAlternative> findAlternativesBySnapshotId(
            @Param("snapshotId") Long snapshotId);
}
