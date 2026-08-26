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
    /** 떠난 사람이 진행 중인 투표에 남겨 둔 표를 정리하는 일만 맡긴다. */
    private final TravelPlanPollService travelPlanPollService;

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
        // 진행 중인 투표에 남겨 둔 표를 걷어 내고, 남은 사람 기준으로 다시 센다.
        travelPlanPollService.onMemberLeft(travelPlanId, member.getId());
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
        // 나가기와 같다. 진행 중인 투표에서 그 사람의 표를 걷어 낸다.
        travelPlanPollService.onMemberLeft(travelPlanId, target.getId());
    }

    /**
     * OWNER 가 내보낸 사람의 재참여를 다시 허용한다.
     * 여기서는 rejoin_allowed 만 올리고 상태는 REMOVED 그대로 둔다.
     * 실제 복귀는 본인이 유효한 초대 링크로 들어올 때 일어나므로,
     * 이 시점에는 자리를 차지하지 않아 정원도 건드리지 않는다.
     */
    @Transactional
    public void allowRejoin(Long userId, Long travelPlanId, Long targetMemberId) {
        requireActivePlan(travelPlanId);
        TravelPlanMember owner = requireActiveMember(travelPlanId, userId);
        if (owner.getRole() != TravelPlanRole.OWNER) {
            // 권한이 없는 사람에게는 대상의 존재 자체를 알리지 않는다.
            throw planNotFound();
        }

        TravelPlanMember target = targetMemberId == null
                ? null : travelPlanMapper.findMemberByPlanAndId(travelPlanId, targetMemberId);
        // 내보내진 MEMBER 만 대상이다. 다른 방의 memberId 는 방 조건에서 걸린다.
        if (target == null
                || target.getStatus() != TravelPlanMemberStatus.REMOVED
                || target.getRole() != TravelPlanRole.MEMBER
                || Boolean.TRUE.equals(target.getRejoinAllowed())) {
            throw planNotFound();
        }

        if (travelPlanMapper.allowMemberRejoin(target.getId(), travelPlanId,
                TravelPlanMemberStatus.REMOVED.name(), TravelPlanRole.MEMBER.name()) != 1) {
            throw planNotFound();
        }
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    /**
     * 방장을 같은 방의 다른 ACTIVE MEMBER 에게 넘긴다.
     * 방 row 를 잠근 뒤 양쪽 상태를 다시 확인하므로 동시에 두 번 넘겨도
     * 방장이 둘이 되거나 없어지지 않는다.
     *
     * <p>두 row 모두 지우거나 새로 만들지 않고 role 만 바꾸므로
     * member.id / user_id / display_name / status 와 작성 기록이 그대로 남는다.
     * 넘긴 사람은 방에서 빠지지 않고 ACTIVE MEMBER 로 남는다.
     */
    @Transactional
    public void transferOwnership(Long userId, Long travelPlanId, Long targetMemberId) {
        if (userId == null) {
            throw new TravelPlanValidationException("userId", "로그인이 필요합니다.");
        }
        // 권한의 기준 자체를 바꾸는 작업이라 방 row 를 잠그고 시작한다.
        if (travelPlanId == null || travelPlanMapper.findPlanByIdAndStatusForUpdate(
                travelPlanId, TravelPlanStatus.ACTIVE.name()) == null) {
            throw planNotFound();
        }

        // 잠근 뒤 현재 방장이 맞는지 다시 본다.
        TravelPlanMember owner = requireActiveMember(travelPlanId, userId);
        if (owner.getRole() != TravelPlanRole.OWNER) {
            throw planNotFound();
        }

        TravelPlanMember target = targetMemberId == null
                ? null : travelPlanMapper.findMemberByPlanAndId(travelPlanId, targetMemberId);
        // 다른 방의 memberId, 나갔거나 내보내진 사람, 자기 자신은 대상이 될 수 없다.
        if (target == null
                || target.getStatus() != TravelPlanMemberStatus.ACTIVE
                || target.getRole() != TravelPlanRole.MEMBER
                || target.getId().equals(owner.getId())) {
            throw planNotFound();
        }

        // 먼저 내려놓고 넘긴다. 중간에도 방장이 둘인 순간이 없다.
        if (travelPlanMapper.changeMemberRole(owner.getId(), travelPlanId,
                TravelPlanMemberStatus.ACTIVE.name(),
                TravelPlanRole.OWNER.name(), TravelPlanRole.MEMBER.name()) != 1) {
            throw planNotFound();
        }
        // 여기서 실패하면 위의 변경까지 함께 되돌아간다(방장 0명 상태로 남지 않는다).
        if (travelPlanMapper.changeMemberRole(target.getId(), travelPlanId,
                TravelPlanMemberStatus.ACTIVE.name(),
                TravelPlanRole.MEMBER.name(), TravelPlanRole.OWNER.name()) != 1) {
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
