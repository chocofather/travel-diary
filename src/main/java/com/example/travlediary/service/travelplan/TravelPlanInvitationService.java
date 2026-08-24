package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanInvitePreviewDto;
import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanInvitation;
import com.example.travlediary.model.TravelPlanInvitationStatus;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.repository.travelplan.TravelPlanInvitationMapper;
import com.example.travlediary.repository.travelplan.TravelPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * 초대 링크 관리.
 * 발급/재발급/비활성화는 방의 ACTIVE OWNER 만 할 수 있고,
 * raw token 은 발급 응답에서 한 번만 나가고 저장되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TravelPlanInvitationService {

    private final TravelPlanMapper travelPlanMapper;
    private final TravelPlanInvitationMapper travelPlanInvitationMapper;

    /**
     * 첫 초대 링크 발급.
     * 이미 살아 있는 링크가 있으면 새로 만들지 않는다(방마다 ACTIVE 는 1건).
     * 기존 링크를 버리고 새로 받으려면 재발급을 쓴다.
     *
     * @return 사용자에게 한 번만 보여 줄 raw token
     */
    @Transactional
    public String createInvitation(Long userId, Long travelPlanId) {
        requireActiveOwner(userId, travelPlanId);

        if (findActiveInvitation(travelPlanId) != null) {
            throw new TravelPlanValidationException("invitation",
                    "이미 활성화된 초대 링크가 있습니다. 새로 만들려면 재발급해 주세요.");
        }
        return issueInvitation(userId, travelPlanId);
    }

    /**
     * 재발급. 기존 ACTIVE 링크를 REPLACED 로 끄고 새 링크를 만든다.
     * 끄는 즉시 예전 링크는 해석되지 않는다.
     *
     * @return 사용자에게 한 번만 보여 줄 새 raw token
     */
    @Transactional
    public String regenerateInvitation(Long userId, Long travelPlanId) {
        requireActiveOwner(userId, travelPlanId);

        travelPlanInvitationMapper.invalidateActiveInvitation(travelPlanId,
                TravelPlanInvitationStatus.ACTIVE.name(),
                TravelPlanInvitationStatus.REPLACED.name());
        return issueInvitation(userId, travelPlanId);
    }

    /**
     * 비활성화. 살아 있는 링크를 DISABLED 로 끈다.
     * 끌 링크가 없으면 이미 원하는 상태이므로 조용히 지나간다(no-op).
     */
    @Transactional
    public void disableInvitation(Long userId, Long travelPlanId) {
        requireActiveOwner(userId, travelPlanId);

        travelPlanInvitationMapper.invalidateActiveInvitation(travelPlanId,
                TravelPlanInvitationStatus.ACTIVE.name(),
                TravelPlanInvitationStatus.DISABLED.name());
    }

    /**
     * 플래너 화면에서 "지금 링크가 켜져 있는지"만 본다.
     * raw token 은 복원할 수 없으므로 링크 문자열은 돌려주지 않는다.
     * OWNER 가 아니면 화면에 초대 영역 자체를 두지 않으므로 false 다.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveInvitation(Long userId, Long travelPlanId) {
        if (userId == null || travelPlanId == null || !isActiveOwner(userId, travelPlanId)) {
            return false;
        }
        return findActiveInvitation(travelPlanId) != null;
    }

    /**
     * 초대 링크를 연 사람에게 보여 줄 방 정보.
     * REPLACED / DISABLED / 없는 토큰 / ACTIVE 가 아닌 방은 모두 빈 결과로 돌려주고,
     * 어느 쪽에서 걸렸는지는 구분해서 알리지 않는다.
     *
     * @param userId 비로그인이면 null
     */
    @Transactional(readOnly = true)
    public Optional<TravelPlanInvitePreviewDto> resolvePreview(Long userId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        TravelPlanInvitation invitation = travelPlanInvitationMapper.findActiveByTokenHash(
                TravelPlanInviteToken.hash(rawToken), TravelPlanInvitationStatus.ACTIVE.name());
        if (invitation == null) {
            return Optional.empty();
        }

        TravelPlan plan = travelPlanMapper.findPlanByIdAndStatus(
                invitation.getTravelPlanId(), TravelPlanStatus.ACTIVE.name());
        if (plan == null) {
            return Optional.empty();
        }

        TravelPlanMember owner = travelPlanMapper.findMemberByPlanAndRole(plan.getId(),
                TravelPlanRole.OWNER.name(), TravelPlanMemberStatus.ACTIVE.name());
        int memberCount = travelPlanMapper.countMembersByPlanAndStatus(
                plan.getId(), TravelPlanMemberStatus.ACTIVE.name());

        return Optional.of(new TravelPlanInvitePreviewDto(
                plan.getId(),
                plan.getTitle(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getRepresentativeImageUrl(),
                memberCount,
                owner == null ? null : owner.getDisplayName(),
                isActiveMember(userId, plan.getId())));
    }

    /** 새 토큰을 만들어 해시만 저장하고, raw token 을 호출자에게 한 번 돌려준다. */
    private String issueInvitation(Long userId, Long travelPlanId) {
        String rawToken = TravelPlanInviteToken.newRawToken();

        TravelPlanInvitation invitation = new TravelPlanInvitation();
        invitation.setTravelPlanId(travelPlanId);
        invitation.setCreatedByUserId(userId);
        // 저장하는 것은 해시뿐이다. raw token 은 이 메서드 밖으로만 나간다.
        invitation.setTokenHash(TravelPlanInviteToken.hash(rawToken));
        invitation.setStatus(TravelPlanInvitationStatus.ACTIVE);

        if (travelPlanInvitationMapper.insertInvitation(invitation) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "초대 링크를 만들지 못했습니다.");
        }
        return rawToken;
    }

    private TravelPlanInvitation findActiveInvitation(Long travelPlanId) {
        return travelPlanInvitationMapper.findActiveByPlanId(
                travelPlanId, TravelPlanInvitationStatus.ACTIVE.name());
    }

    /**
     * 초대 링크를 다룰 수 있는 사람인지 확인한다.
     * OWNER 여부는 travel_plans.created_by_user_id 가 아니라
     * travel_plan_members(role=OWNER, status=ACTIVE)를 기준으로 본다.
     */
    private void requireActiveOwner(Long userId, Long travelPlanId) {
        if (userId == null) {
            throw new TravelPlanValidationException("userId", "로그인이 필요합니다.");
        }
        if (travelPlanId == null || !isActiveOwner(userId, travelPlanId)) {
            throw planNotFound();
        }
        if (travelPlanMapper.findPlanByIdAndStatus(
                travelPlanId, TravelPlanStatus.ACTIVE.name()) == null) {
            throw planNotFound();
        }
    }

    private boolean isActiveOwner(Long userId, Long travelPlanId) {
        TravelPlanMember member = travelPlanMapper.findMemberByPlanAndUser(
                travelPlanId, userId, TravelPlanMemberStatus.ACTIVE.name());
        return member != null && member.getRole() == TravelPlanRole.OWNER;
    }

    /** 이미 들어와 있는 사람인지. 다음 단계의 참여 처리에서도 이 판별을 그대로 쓴다. */
    private boolean isActiveMember(Long userId, Long travelPlanId) {
        if (userId == null) {
            return false;
        }
        return travelPlanMapper.findMemberByPlanAndUser(
                travelPlanId, userId, TravelPlanMemberStatus.ACTIVE.name()) != null;
    }

    /** 권한이 없는 방의 존재 자체를 알리지 않도록 404 로 처리한다(다이어리와 같은 관례). */
    private ResponseStatusException planNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다.");
    }
}
