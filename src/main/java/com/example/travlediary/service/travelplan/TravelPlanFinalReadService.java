package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanFinalDetailDto;
import com.example.travlediary.dto.TravelPlanFinalListItemDto;
import com.example.travlediary.model.TravelPlanFinalItem;
import com.example.travlediary.model.TravelPlanFinalItemAlternative;
import com.example.travlediary.model.TravelPlanFinalSnapshot;
import com.example.travlediary.repository.travelplan.TravelPlanFinalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 완료된 여행 읽기.
 *
 * <p>최종본은 한번 만들어지면 바뀌지 않는다. 여기서는 읽기만 한다.
 * 원본 방(travel_plans / travel_plan_items ...)을 다시 들여다보지 않으므로,
 * 나중에 원본이 어떻게 되든 완료된 여행은 그때 모습 그대로 보인다.
 *
 * <p>볼 수 있는 사람은 완료 시점에 그 여행에 있던 사람뿐이다.
 * 방장이든 아니든 같은 최종본을 본다.
 */
@Service
@RequiredArgsConstructor
public class TravelPlanFinalReadService {

    private final TravelPlanFinalMapper travelPlanFinalMapper;

    /** 내가 함께했던 완료된 여행 목록. 최근에 끝난 것부터 온다. */
    @Transactional(readOnly = true)
    public List<TravelPlanFinalListItemDto> getCompletedPlans(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<TravelPlanFinalListItemDto> plans =
                travelPlanFinalMapper.findSnapshotsByUserId(userId);
        return plans == null ? List.of() : plans;
    }

    /**
     * 완료된 여행 한 벌.
     *
     * <p>그 여행에 함께했던 사람이 아니면 최종본의 존재 자체를 알리지 않는다
     * (다른 방 조회와 같은 관례로 404).
     */
    @Transactional(readOnly = true)
    public TravelPlanFinalDetailDto getCompletedPlanDetail(Long userId, Long travelPlanId) {
        if (userId == null || travelPlanId == null) {
            throw finalPlanNotFound();
        }
        // 볼 자격은 이 조회 하나로 확인된다. 함께하지 않았으면 여기서 비어 온다.
        TravelPlanFinalSnapshot snapshot =
                travelPlanFinalMapper.findSnapshotByPlanAndUser(travelPlanId, userId);
        if (snapshot == null) {
            throw finalPlanNotFound();
        }

        Long snapshotId = snapshot.getId();
        return new TravelPlanFinalDetailDto(
                snapshot,
                travelPlanFinalMapper.findMembersBySnapshotId(snapshotId),
                travelPlanFinalMapper.findDaysBySnapshotId(snapshotId),
                // 날짜·일정 수만큼 조회가 나가지 않도록 한 번에 읽어 묶는다.
                groupBy(travelPlanFinalMapper.findItemsBySnapshotId(snapshotId),
                        TravelPlanFinalItem::getFinalDayId),
                groupBy(travelPlanFinalMapper.findAlternativesBySnapshotId(snapshotId),
                        TravelPlanFinalItemAlternative::getFinalItemId));
    }

    /** 읽어 온 순서를 그대로 지키며 묶는다. 완료 시점의 차례가 화면에 그대로 나온다. */
    private <T> Map<Long, List<T>> groupBy(List<T> rows, Function<T, Long> key) {
        return rows == null
                ? Map.of()
                : rows.stream().collect(Collectors.groupingBy(
                        key, LinkedHashMap::new, Collectors.toList()));
    }

    private ResponseStatusException finalPlanNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "완료된 여행을 찾을 수 없습니다.");
    }
}
