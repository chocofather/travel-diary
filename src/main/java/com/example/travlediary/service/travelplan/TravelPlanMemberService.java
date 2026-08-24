package com.example.travlediary.service.travelplan;

import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.repository.travelplan.TravelPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 참여자 상태 변경.
 * 이번 단계는 MEMBER 가 스스로 나가는 것과 OWNER 가 MEMBER 를 내보내는 것 둘뿐이다.
 *
 * <p>어느 쪽도 travel_plan_members row 를 지우지 않는다.
 * 과거 참여 기록과 일정/대안의 created_by_member_id 가 그대로 남아야 하기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class TravelPlanMemberService {

    private final TravelPlanMapper travelPlanMapper;

    /**
     * 스스로 여행에서 나간다. ACTIVE 였던 MEMBER 만 가능하다.
     * OWNER 는 방장을 넘긴 뒤에야 나갈 수 있어 여기서는 막는다.
     */
    @Transactional
    public void leave(Long userId, Long travelPlanId) {
        requireActivePlan(travelPlanId);
        TravelPlanMember member = requireActiveMember(travelPlanId, userId);

        if (member.getRole() == TravelPlanRole.OWNER) {
            throw new TravelPlanValidationException("role",
                    "방장은 바로 나갈 수 없습니다. 먼저 다른 참여자에게 방장을 넘겨주세요.");
        }

        // 조건부 UPDATE 라 두 번 눌러도 한 번만 반영된다.
        if (travelPlanMapper.markMemberLeft(member.getId(), travelPlanId,
                TravelPlanMemberStatus.ACTIVE.name(),
                TravelPlanMemberStatus.LEFT.name(),
                TravelPlanRole.MEMBER.name()) != 1) {
            throw planNotFound();
        }
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    /**
     * OWNER 가 다른 참여자를 내보낸다.
     * 대상은 같은 방의 ACTIVE MEMBER 여야 하며, OWNER 자신은 대상이 될 수 없다.
     */
    @Transactional
    public void removeMember(Long userId, Long travelPlanId, Long targetMemberId) {
        requireActivePlan(travelPlanId);
        TravelPlanMember owner = requireActiveMember(travelPlanId, userId);
        if (owner.getRole() != TravelPlanRole.OWNER) {
            // 권한이 없는 사람에게는 대상의 존재 자체를 알리지 않는다.
            throw planNotFound();
        }

        TravelPlanMember target = targetMemberId == null
                ? null : travelPlanMapper.findMemberByPlanAndId(travelPlanId, targetMemberId);
        // 다른 방의 memberId 를 섞어 보내도 방 조건에서 걸린다.
        if (target == null
                || target.getStatus() != TravelPlanMemberStatus.ACTIVE
                || target.getRole() != TravelPlanRole.MEMBER
                || target.getId().equals(owner.getId())) {
            throw planNotFound();
        }

        if (travelPlanMapper.markMemberRemoved(target.getId(), travelPlanId,
                TravelPlanMemberStatus.ACTIVE.name(),
                TravelPlanMemberStatus.REMOVED.name(),
                TravelPlanRole.MEMBER.name()) != 1) {
            throw planNotFound();
        }
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    private void requireActivePlan(Long travelPlanId) {
        if (travelPlanId == null || travelPlanMapper.findPlanByIdAndStatus(
                travelPlanId, TravelPlanStatus.ACTIVE.name()) == null) {
            throw planNotFound();
        }
    }

    private TravelPlanMember requireActiveMember(Long travelPlanId, Long userId) {
        if (userId == null) {
            throw new TravelPlanValidationException("userId", "로그인이 필요합니다.");
        }
        TravelPlanMember member = travelPlanMapper.findMemberByPlanAndUser(
                travelPlanId, userId, TravelPlanMemberStatus.ACTIVE.name());
        if (member == null) {
            throw planNotFound();
        }
        return member;
    }

    /** 권한이 없는 방의 존재 자체를 알리지 않도록 404 로 처리한다(다이어리와 같은 관례). */
    private ResponseStatusException planNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다.");
    }
}
