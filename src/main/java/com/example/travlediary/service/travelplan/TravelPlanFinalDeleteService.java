package com.example.travlediary.service.travelplan;

import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.repository.travelplan.TravelPlanFinalMapper;
import com.example.travlediary.repository.travelplan.TravelPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 완료된 여행 지우기.
 *
 * <p>사용자에게는 언제나 "내 목록에서 삭제" 하나로 보이지만, 안에서 벌어지는 일은 둘이다.
 *
 * <ul>
 *   <li>아직 이 여행을 보관 중인 사람이 남아 있으면 — 내 목록에서만 치운다.
 *       최종본도, 원본 방의 채팅·투표·일정도 그대로 둔다.</li>
 *   <li>내가 마지막 한 사람이었으면 — 이제 아무도 볼 수 없는 여행이므로 실제로 지운다.</li>
 * </ul>
 *
 * <p>기준은 지운 사람 수가 아니라 <em>남은</em> 사람 수다.
 * 한 명이라도 아직 보관 중이면 아무것도 사라지지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TravelPlanFinalDeleteService {

    private final TravelPlanMapper travelPlanMapper;
    private final TravelPlanFinalMapper travelPlanFinalMapper;

    /**
     * 완료된 여행을 내 목록에서 지운다. 마지막 한 사람이었다면 여행 자체가 사라진다.
     *
     * <p>순서가 곧 안전장치다.
     * <ol>
     *   <li>완료된 방의 row 를 잠근다. 여기서부터 이 방의 삭제는 한 줄로 세워진다.</li>
     *   <li>내 명단 행에 지운 시각을 적는다. 자기 것이 아니거나 이미 지웠으면 여기서 걸린다.</li>
     *   <li>잠금 안에서 남은 사람을 센다.</li>
     *   <li>0 명일 때만 방을 지운다. 딸린 데이터는 CASCADE 로 함께 사라진다.</li>
     * </ol>
     *
     * <p>마지막 두 사람이 거의 동시에 눌러도 잠금 때문에 한 사람씩 지나간다.
     * 먼저 지나간 쪽이 방을 지우고, 뒤에 오는 쪽은 잠금이 풀린 뒤 방이 없는 것을 보고
     * 아무것도 하지 않는다. 그래서 전체 삭제는 한 번뿐이다.
     *
     * <p>진행 중인 방(ACTIVE)에는 어떤 경로로도 닿지 않는다.
     * 잠글 때도 지울 때도 COMPLETED 조건을 함께 건다.
     *
     * @return true 면 이 호출로 여행 자체가 사라졌고, false 면 내 목록에서만 치웠다.
     */
    @Transactional
    public boolean deleteForMe(Long userId, Long travelPlanId) {
        if (userId == null || travelPlanId == null) {
            throw finalPlanNotFound();
        }

        /*
          여기서부터 이 방의 삭제는 한 줄로 세워진다.
          세는 것과 지우는 것이 이 잠금 안에서 일어나야
          "둘 다 아직 남아 있다고 보는" 순간이 생기지 않는다.
        */
        if (travelPlanMapper.findPlanByIdAndStatusForUpdate(
                travelPlanId, TravelPlanStatus.COMPLETED.name()) == null) {
            // 진행 중이거나, 그 사이 이미 사라진 방이다.
            throw finalPlanNotFound();
        }

        // 자격 확인과 지우기가 한 문장이다. 남의 것을 대신 지울 길이 없다.
        if (travelPlanFinalMapper.hideSnapshotForUser(travelPlanId, userId) != 1) {
            throw finalPlanNotFound();
        }

        // 지운 사람 수가 아니라 남은 사람 수를 본다.
        if (travelPlanFinalMapper.countVisibleMembersByPlanId(travelPlanId) > 0) {
            // 한 명이라도 보관 중이면 최종본도 원본 방도 그대로 둔다.
            return false;
        }

        /*
          이제 아무도 볼 수 없는 여행이다.
          최종본과 원본 방, 그리고 딸린 참여자·설정·초대·일정·대안·채팅·투표가
          travel_plans 를 향한 CASCADE 로 함께 사라진다.
        */
        return travelPlanMapper.deletePlanByIdAndStatus(
                travelPlanId, TravelPlanStatus.COMPLETED.name()) == 1;
    }

    /** 자기 것이 아니면 그 최종본이 있는지조차 알리지 않는다(다른 방 조회와 같은 관례). */
    private ResponseStatusException finalPlanNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "완료된 여행을 찾을 수 없습니다.");
    }
}
