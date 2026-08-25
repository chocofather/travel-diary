package com.example.travlediary.service.travelplan;

import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.repository.travelplan.TravelPlanMapper;
import com.example.travlediary.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Optional;

/**
 * 실시간 연결이 그 방을 볼 자격이 있는지 확인한다.
 *
 * <p>URL 이나 클라이언트가 보낸 값을 믿지 않고, HTTP 쪽과 똑같이
 * travel_plans(status=ACTIVE) 와 travel_plan_members(status=ACTIVE) 를 기준으로 본다.
 * LEFT / REMOVED / 비참여자는 여기서 걸린다.
 */
@Component
@RequiredArgsConstructor
public class TravelPlanRoomAccess {

    private final TravelPlanMapper travelPlanMapper;

    /**
     * 지금 연결한 사람이 그 방의 ACTIVE 참여자인지 보고 방 안에서의 id 를 돌려준다.
     *
     * @return 참여자가 아니거나 방이 끝났으면 비어 있는 결과
     */
    @Transactional(readOnly = true)
    public Optional<Long> findActiveMemberId(Principal principal, Long travelPlanId) {
        Long userId = userIdOf(principal);
        if (userId == null || travelPlanId == null) {
            return Optional.empty();
        }
        if (travelPlanMapper.findPlanByIdAndStatus(
                travelPlanId, TravelPlanStatus.ACTIVE.name()) == null) {
            return Optional.empty();
        }
        TravelPlanMember member = travelPlanMapper.findMemberByPlanAndUser(
                travelPlanId, userId, TravelPlanMemberStatus.ACTIVE.name());
        return member == null ? Optional.empty() : Optional.ofNullable(member.getId());
    }

    /**
     * STOMP 연결에 실린 로그인 정보에서 사용자를 꺼낸다.
     * 핸드셰이크가 기존 HTTP 세션을 그대로 쓰므로 여기 담긴 값이 곧 로그인한 사람이다.
     */
    public Long userIdOf(Principal principal) {
        if (!(principal instanceof Authentication authentication)) {
            return null;
        }
        if (!authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof CustomUserDetails userDetails
                ? userDetails.getId()
                : null;
    }
}
