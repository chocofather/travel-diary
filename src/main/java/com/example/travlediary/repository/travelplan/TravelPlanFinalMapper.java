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
     * 완료 시점에 이 사람이 그 여행에 있었고, 아직 자기 목록에 두고 있는지.
     * 완료된 방으로 들어왔을 때 무엇을 안내할지 가르는 데 쓴다.
     * 내 목록에서 지운 사람에게 완료된 여행 목록을 가리킬 수는 없다.
     */
    boolean existsMemberByPlanAndUser(@Param("travelPlanId") Long travelPlanId,
                                      @Param("userId") Long userId);

    /**
     * 완료된 여행을 그 사람의 목록에서만 치운다.
     *
     * <p>최종본(스냅숏·날짜·일정·대안)도, 다른 사람의 명단 행도 건드리지 않는다.
     * 자기 행의 hidden_at 만 채우므로 함께한 다른 사람은 그대로 본다.
     * 이미 지운 뒤에는 반영되지 않아 두 번 눌러도 한 번만 처리된다.
     *
     * @return 1 이면 방금 지웠고, 0 이면 그 사람의 완료본이 아니거나 이미 지운 뒤다.
     */
    int hideSnapshotForUser(@Param("travelPlanId") Long travelPlanId,
                            @Param("userId") Long userId);

    /**
     * 아직 이 완료된 여행을 자기 목록에 두고 있는 사람 수.
     *
     * <p>지운 사람 수가 아니라 <em>남은</em> 사람 수다.
     * 0 이면 이제 아무도 볼 수 없는 여행이라는 뜻이라 그때 비로소 실제로 지운다.
     * 반드시 방 row 를 잠근 뒤에 센다. 그러지 않으면 마지막 두 사람이
     * 동시에 눌렀을 때 둘 다 "아직 남아 있다" 로 볼 수 있다.
     */
    int countVisibleMembersByPlanId(@Param("travelPlanId") Long travelPlanId);

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
