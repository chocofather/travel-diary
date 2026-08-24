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
import org.springframework.dao.DuplicateKeyException;
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

    /** 방 정원. OWNER 를 포함한 ACTIVE 참여자 수 기준이다. */
    public static final int MAX_MEMBERS = 8;

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
        // 비로그인이면 볼 것이 없으므로 참여 기록을 읽지 않는다.
        TravelPlanMember existing = userId == null
                ? null : travelPlanMapper.findAnyMemberByPlanAndUser(plan.getId(), userId);

        return Optional.of(new TravelPlanInvitePreviewDto(
                plan.getId(),
                plan.getTitle(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getRepresentativeImageUrl(),
                memberCount,
                MAX_MEMBERS,
                owner == null ? null : owner.getDisplayName(),
                isActive(existing),
                isRejoinBlocked(existing),
                canRejoin(existing),
                canRejoin(existing) ? existing.getDisplayName() : null));
    }

    /**
     * 초대 링크로 새 참여자를 등록한다.
     * 방 row 를 잠근 뒤 정원과 상태를 다시 확인하므로 동시에 들어와도 8명을 넘길 수 없고,
     * 화면을 열어 둔 사이에 OWNER 가 링크를 껐다면 여기서 걸린다.
     *
     * @return 들어간 방의 id. 이미 들어와 있던 사람이면 그대로 그 방의 id 를 돌려준다.
     */
    @Transactional
    public Long join(Long userId, String rawToken, String displayName) {
        if (userId == null) {
            throw new TravelPlanValidationException("userId", "로그인이 필요합니다.");
        }
        // 잠그기 전에 끝낼 수 있는 확인은 먼저 한다.
        // 이름은 신규 참여에서만 받으므로(재참여는 쓰던 이름을 그대로 쓴다)
        // 어느 쪽인지 가려진 뒤에 검사한다.
        TravelPlanInvitation invitation = requireActiveInvitation(rawToken);

        // 여기서부터 이 방의 참여는 한 줄로 세워진다.
        TravelPlan plan = travelPlanMapper.findPlanByIdAndStatusForUpdate(
                invitation.getTravelPlanId(), TravelPlanStatus.ACTIVE.name());
        if (plan == null) {
            throw invalidInvitation();
        }
        Long travelPlanId = plan.getId();
        // 화면을 보고 있는 동안 링크가 꺼졌거나 재발급되었을 수 있다.
        requireActiveInvitation(rawToken);

        TravelPlanMember existing = travelPlanMapper.findAnyMemberByPlanAndUser(
                travelPlanId, userId);
        // 더블클릭이나 탭 두 개로 두 번 들어와도 row 를 하나 더 만들지 않는다.
        if (isActive(existing)) {
            return travelPlanId;
        }

        // 다시 들어오는 사람도 자리를 하나 차지하므로 정원은 똑같이 본다.
        if (travelPlanMapper.countMembersByPlanAndStatus(
                travelPlanId, TravelPlanMemberStatus.ACTIVE.name()) >= MAX_MEMBERS) {
            throw new TravelPlanValidationException("capacity", "참여 인원이 모두 찼어요.");
        }

        if (existing != null) {
            // 나갔던 사람은 쓰던 자리로 돌아온다. 새 row 를 만들지 않는다.
            reactivate(existing, travelPlanId, userId);
            travelPlanMapper.touchLastActivity(travelPlanId);
            return travelPlanId;
        }

        String normalizedDisplayName = TravelPlanDisplayName.normalize(displayName);
        if (travelPlanMapper.countMembersByPlanAndDisplayName(
                travelPlanId, normalizedDisplayName) > 0) {
            throw duplicateDisplayName();
        }

        if (insertMember(userId, travelPlanId, normalizedDisplayName)) {
            travelPlanMapper.touchLastActivity(travelPlanId);
        }
        return travelPlanId;
    }

    /**
     * 스스로 나갔던 사람을 원래 자리로 되돌린다.
     * id / display_name / role 을 그대로 두므로 그 사람이 쓴 일정·대안의
     * created_by_member_id 연결이 끊기지 않는다.
     * 내보내진 사람(rejoin_allowed = 0)은 여기서 걸린다.
     */
    private void reactivate(TravelPlanMember existing, Long travelPlanId, Long userId) {
        if (!canRejoin(existing)) {
            throw rejoinBlocked();
        }
        if (travelPlanMapper.reactivateLeftMember(existing.getId(), travelPlanId, userId,
                TravelPlanMemberStatus.LEFT.name(),
                TravelPlanMemberStatus.ACTIVE.name()) != 1) {
            throw rejoinBlocked();
        }
    }

    /**
     * 유효한 초대 링크만으로 다시 들어올 수 있는 사람인지.
     * 스스로 나갔고(LEFT) 재참여가 막히지 않은 경우만 참이다.
     * 내보내진 사람(REMOVED)은 OWNER 가 따로 풀어 주기 전까지 돌아올 수 없다.
     */
    private boolean canRejoin(TravelPlanMember member) {
        return member != null
                && member.getStatus() == TravelPlanMemberStatus.LEFT
                && Boolean.TRUE.equals(member.getRejoinAllowed());
    }

    /**
     * 초대로 들어오는 사람은 언제나 MEMBER 다. OWNER 는 이 경로로 생기지 않는다.
     * UNIQUE 제약은 최종 방어선이라, 걸리면 500 대신 상황에 맞는 안내로 바꾼다.
     *
     * @return 이번 요청이 실제로 참여시켰으면 true,
     *         그 사이 같은 사람의 다른 요청이 먼저 끝났으면 false
     */
    private boolean insertMember(Long userId, Long travelPlanId, String displayName) {
        TravelPlanMember member = new TravelPlanMember();
        member.setTravelPlanId(travelPlanId);
        member.setUserId(userId);
        member.setDisplayName(displayName);
        member.setRole(TravelPlanRole.MEMBER);
        member.setStatus(TravelPlanMemberStatus.ACTIVE);

        try {
            if (travelPlanMapper.insertMember(member) != 1) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "여행 계획에 참여하지 못했습니다.");
            }
            return true;
        } catch (DuplicateKeyException exception) {
            // (plan, user) 가 걸렸는지 (plan, display_name) 이 걸렸는지 나눠서 알린다.
            TravelPlanMember stored = travelPlanMapper.findAnyMemberByPlanAndUser(
                    travelPlanId, userId);
            if (stored == null) {
                throw duplicateDisplayName();
            }
            if (isActive(stored)) {
                return false;
            }
            throw rejoinBlocked();
        }
    }

    /** raw token 은 저장하지 않으므로 언제나 해시로만 찾는다. */
    private TravelPlanInvitation requireActiveInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidInvitation();
        }
        TravelPlanInvitation invitation = travelPlanInvitationMapper.findActiveByTokenHash(
                TravelPlanInviteToken.hash(rawToken), TravelPlanInvitationStatus.ACTIVE.name());
        if (invitation == null) {
            throw invalidInvitation();
        }
        return invitation;
    }

    /** 없는 토큰 / REPLACED / DISABLED / 끝난 방을 구분하지 않고 같은 안내로 돌려준다. */
    private TravelPlanValidationException invalidInvitation() {
        return new TravelPlanValidationException("invitation",
                "유효하지 않거나 만료된 초대 링크입니다.");
    }

    private TravelPlanValidationException rejoinBlocked() {
        return new TravelPlanValidationException("membership",
                "현재 이 여행에 다시 참여할 수 없습니다.");
    }

    private TravelPlanValidationException duplicateDisplayName() {
        return new TravelPlanValidationException("displayName", "이미 사용 중인 이름입니다.");
    }

    private boolean isActive(TravelPlanMember member) {
        return member != null && member.getStatus() == TravelPlanMemberStatus.ACTIVE;
    }

    /**
     * 이 링크만으로는 돌아올 수 없는 기록.
     * 내보내진 사람(REMOVED)과 재참여가 막힌 사람이 여기 해당한다.
     * 스스로 나간 사람은 canRejoin 으로 빠지므로 막히지 않는다.
     */
    private boolean isRejoinBlocked(TravelPlanMember member) {
        return member != null
                && member.getStatus() != TravelPlanMemberStatus.ACTIVE
                && !canRejoin(member);
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

    /** 권한이 없는 방의 존재 자체를 알리지 않도록 404 로 처리한다(다이어리와 같은 관례). */
    private ResponseStatusException planNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다.");
    }
}
