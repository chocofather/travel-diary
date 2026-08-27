package com.example.travlediary.config;

import com.example.travlediary.service.travelplan.TravelPlanChatDestinations;
import com.example.travlediary.service.travelplan.TravelPlanEditorDestinations;
import com.example.travlediary.service.travelplan.TravelPlanMemberDestinations;
import com.example.travlediary.service.travelplan.TravelPlanPollDestinations;
import com.example.travlediary.service.travelplan.TravelPlanPresenceDestinations;
import com.example.travlediary.service.travelplan.TravelPlanRoomAccess;
import com.example.travlediary.service.travelplan.TravelPlanRoomSessionRegistry;
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
    /** 자격을 잃었을 때 끊을 수 있도록, 구독이 받아들여진 연결을 적어 둔다. */
    private final TravelPlanRoomSessionRegistry travelPlanRoomSessionRegistry;

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
            // 지금 쓰는 topic 은 방별 접속 표시 / 일정 변경 / 작성 중 상태 /
            // 참여자 명단 / 채팅 / 투표뿐이다.
            if (travelPlanId == null) {
                throw new AccessDeniedException("구독할 수 없는 대상입니다.");
            }
            Long memberId = requireActiveMember(principal, travelPlanId);
            /*
              이 연결이 그 방을 보기 시작했다고 적어 둔다.
              구독은 여기서 한 번만 검사되므로, 나중에 방에서 빠지면
              그때는 이 장부를 보고 연결 자체를 끊는다.
            */
            travelPlanRoomSessionRegistry.watching(
                    travelPlanId, memberId, accessor.getSessionId());
        }

        // 보내는 쪽도 막아야 한다. 목적지에 적힌 방 번호를 믿지 않는다.
        if (command == StompCommand.SEND) {
            Principal principal = requirePrincipal(accessor.getUser());
            Long travelPlanId = sendableTravelPlanId(accessor.getDestination());
            if (travelPlanId == null) {
                throw new AccessDeniedException("보낼 수 없는 대상입니다.");
            }
            // 나갔거나 내보내진 뒤에도 연결이 남아 있을 수 있다. 보낼 때마다 다시 본다.
            requireActiveMember(principal, travelPlanId);
        }
        return message;
    }

    /** 자기 앞으로만 오는 개인 큐. Spring 이 사용자별로 갈라 준다. */
    private boolean isOwnReplyQueue(String destination) {
        if (destination == null) {
            return false;
        }
        return destination.startsWith("/user" + TravelPlanEditorDestinations.LOCK_REPLY_QUEUE)
                || destination.startsWith("/user" + TravelPlanChatDestinations.REPLY_QUEUE)
                || destination.startsWith("/user" + TravelPlanMemberDestinations.ACCESS_QUEUE);
    }

    /**
     * 보내도 되는 목적지에서 방 번호를 꺼낸다.
     * 어느 쪽이든 그 방의 ACTIVE 참여자인지는 똑같이 확인한다.
     *
     * @return 허용하지 않는 목적지면 null
     */
    private Long sendableTravelPlanId(String destination) {
        Long editorPlanId = TravelPlanEditorDestinations.sendTravelPlanIdOf(destination);
        if (editorPlanId != null) {
            return editorPlanId;
        }
        Long presencePlanId = TravelPlanPresenceDestinations.joinTravelPlanIdOf(destination);
        return presencePlanId != null
                ? presencePlanId
                : TravelPlanChatDestinations.sendTravelPlanIdOf(destination);
    }

    /** @return 그 방 안에서의 참여 id. 방별로 다른 값이라 다른 방과 섞이지 않는다. */
    private Long requireActiveMember(Principal principal, Long travelPlanId) {
        return travelPlanRoomAccess.findActiveMemberId(principal, travelPlanId)
                .orElseThrow(() -> new AccessDeniedException("여행계획에 참여 중이 아닙니다."));
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
        if (schedulePlanId != null) {
            return schedulePlanId;
        }
        Long editorPlanId = TravelPlanEditorDestinations.travelPlanIdOf(destination);
        if (editorPlanId != null) {
            return editorPlanId;
        }
        // 참여자 명단. 접속 표시와 다른 topic 이라 따로 확인한다.
        Long memberPlanId = TravelPlanMemberDestinations.travelPlanIdOf(destination);
        if (memberPlanId != null) {
            return memberPlanId;
        }
        Long chatPlanId = TravelPlanChatDestinations.travelPlanIdOf(destination);
        return chatPlanId != null
                ? chatPlanId
                : TravelPlanPollDestinations.travelPlanIdOf(destination);
    }

    private Principal requirePrincipal(Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        return principal;
    }
}
