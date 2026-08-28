package com.example.travlediary.service.travelplan;

import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.repository.travelplan.TravelPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 진행 중인 공동 여행계획을 통째로 지운다. 방장만 할 수 있다.
 *
 * <p>참여자를 한 명씩 내보내지 않는다. 방 row 하나를 지우면 참여 기록도 함께 사라져
 * 모두가 그 방에서 빠진다. 딸린 데이터는 travel_plans 를 향한 CASCADE 가 정리한다.
 * 서비스에서 자식 테이블을 하나씩 지우지 않는 이유이기도 하다 —
 * 지우는 순서를 여기서 관리하기 시작하면 테이블이 늘 때마다 빠뜨릴 자리가 생긴다.
 *
 * <p>{@link TravelPlanMemberService} 의 나가기/내보내기와는 다른 일이다.
 * 그쪽은 사람 하나의 status 만 바꾸고 방은 그대로 둔다. 여기서는 방이 없어진다.
 *
 * <p>되돌릴 수 없다. 최종본을 뜨지 않으므로 완료와 달리 남는 것이 없다.
 */
@Service
@RequiredArgsConstructor
public class TravelPlanDeleteService {

    private final TravelPlanMapper travelPlanMapper;
    /** 방이 사라진 사실을 알리는 일만 맡긴다. 실제 전송은 커밋 뒤에 일어난다. */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 방을 지운다.
     *
     * <p>방 row 를 먼저 잠근다. 그래야 같은 순간의 완료(FINALIZING 으로 옮기는 쪽)와
     * 한 줄로 선다. 완료가 먼저 끝났으면 이 방은 더 이상 ACTIVE 가 아니라 여기서 걸리고,
     * 이쪽이 먼저면 완료 쪽이 잠금을 기다렸다가 사라진 방을 보고 멈춘다.
     *
     * <p>자격을 확인하는 자리는 이 메서드 하나뿐이다. 화면에 버튼이 보였는지는 보지 않는다.
     *
     * @throws ResponseStatusException 방장이 아니거나, 없는 방이거나, 이미 끝난 방일 때.
     *                                 사유는 나누지 않는다 — 권한이 없는 사람에게
     *                                 그 방이 있는지조차 알릴 이유가 없다.
     */
    @Transactional
    public void deletePlan(Long userId, Long travelPlanId) {
        if (userId == null) {
            throw new TravelPlanValidationException("userId", "로그인이 필요합니다.");
        }
        if (travelPlanId == null || travelPlanMapper.findPlanByIdAndStatusForUpdate(
                travelPlanId, TravelPlanStatus.ACTIVE.name()) == null) {
            // 없는 방이거나 이미 완료된 방이다. 완료된 여행은 최종본 쪽에서 각자 치운다.
            throw planNotFound();
        }

        TravelPlanMember member = travelPlanMapper.findMemberByPlanAndUser(
                travelPlanId, userId, TravelPlanMemberStatus.ACTIVE.name());
        if (member == null || member.getRole() != TravelPlanRole.OWNER) {
            throw planNotFound();
        }

        /*
          상태 조건을 다시 건다. 잠금을 잡고 있어 바뀔 일이 없지만,
          이 한 줄만 보고도 진행 중인 방에만 닿는다는 것이 읽히게 둔다.
        */
        if (travelPlanMapper.deletePlanByIdAndStatus(
                travelPlanId, TravelPlanStatus.ACTIVE.name()) != 1) {
            throw planNotFound();
        }

        /*
          알리는 것은 커밋이 끝난 뒤다.
          여기서 먼저 내보내면 뒤에서 실패해 삭제가 되돌아갔을 때
          멀쩡한 방에서 사람들이 목록으로 튕겨 나간다.
        */
        eventPublisher.publishEvent(new TravelPlanDeletedEvent(travelPlanId));
    }

    /** 권한이 없는 방의 존재 자체를 알리지 않도록 404 로 처리한다(방 관리와 같은 관례). */
    private ResponseStatusException planNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다.");
    }
}
