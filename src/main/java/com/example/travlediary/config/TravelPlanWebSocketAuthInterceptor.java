package com.example.travlediary.config;

import com.example.travlediary.service.travelplan.TravelPlanEditorDestinations;
import com.example.travlediary.service.travelplan.TravelPlanPresenceDestinations;
import com.example.travlediary.service.travelplan.TravelPlanRoomAccess;
import com.example.travlediary.service.travelplan.TravelPlanScheduleDestinations;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * 들어오는 STOMP 프레임을 먼저 걸러 낸다.
 *
 * <p>연결했다는 것만으로 아무 방이나 구독하게 두면 목적지에 적힌 방 번호를 믿는 셈이 된다.
 * 그래서 구독 시점에 그 방의 ACTIVE 참여자인지 매번 확인한다.
 */
@Component
@RequiredArgsConstructor
public class TravelPlanWebSocketAuthInterceptor implements ChannelInterceptor {

    private final TravelPlanRoomAccess travelPlanRoomAccess;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        // 핸드셰이크에서 이미 로그인 세션을 확인하지만 한 번 더 본다.
        if (command == StompCommand.CONNECT) {
            requirePrincipal(accessor.getUser());
            return message;
        }

        if (command == StompCommand.SUBSCRIBE) {
            Principal principal = requirePrincipal(accessor.getUser());
            String destination = accessor.getDestination();
            // 잠금 결과는 요청한 본인에게만 가는 개인 큐라 방 검사를 따로 하지 않는다.
            if (isOwnReplyQueue(destination)) {
                return message;
            }
            Long travelPlanId = subscribableTravelPlanId(destination);
            // 지금 쓰는 topic 은 방별 접속 표시 / 일정 변경 / 작성 중 상태뿐이다.
            if (travelPlanId == null) {
                throw new AccessDeniedException("구독할 수 없는 대상입니다.");
            }
            requireActiveMember(principal, travelPlanId);
        }

        // 보내는 쪽도 막아야 한다. 목적지에 적힌 방 번호를 믿지 않는다.
        if (command == StompCommand.SEND) {
            Principal principal = requirePrincipal(accessor.getUser());
            Long travelPlanId = TravelPlanEditorDestinations
                    .sendTravelPlanIdOf(accessor.getDestination());
            Long presencePlanId = TravelPlanPresenceDestinations
                    .joinTravelPlanIdOf(accessor.getDestination());
            if (travelPlanId == null && presencePlanId == null) {
                throw new AccessDeniedException("보낼 수 없는 대상입니다.");
            }
            requireActiveMember(principal,
                    travelPlanId != null ? travelPlanId : presencePlanId);
        }
        return message;
    }

    /** 자기 앞으로만 오는 개인 큐. Spring 이 사용자별로 갈라 준다. */
    private boolean isOwnReplyQueue(String destination) {
        return destination != null
                && destination.startsWith("/user" + TravelPlanEditorDestinations.LOCK_REPLY_QUEUE);
    }

    private void requireActiveMember(Principal principal, Long travelPlanId) {
        if (travelPlanRoomAccess.findActiveMemberId(principal, travelPlanId).isEmpty()) {
            throw new AccessDeniedException("여행계획에 참여 중이 아닙니다.");
        }
    }

    /**
     * 구독을 허용하는 목적지에서 방 번호를 꺼낸다.
     * 어느 쪽이든 그 방의 ACTIVE 참여자인지는 똑같이 확인한다.
     *
     * @return 허용하지 않는 목적지면 null
     */
    private Long subscribableTravelPlanId(String destination) {
        Long presencePlanId = TravelPlanPresenceDestinations.travelPlanIdOf(destination);
        if (presencePlanId != null) {
            return presencePlanId;
        }
        Long schedulePlanId = TravelPlanScheduleDestinations.travelPlanIdOf(destination);
        return schedulePlanId != null
                ? schedulePlanId
                : TravelPlanEditorDestinations.travelPlanIdOf(destination);
    }

    private Principal requirePrincipal(Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        return principal;
    }
}
