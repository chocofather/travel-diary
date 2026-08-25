package com.example.travlediary.controller.travelplan;

import com.example.travlediary.dto.TravelPlanPresenceDto;
import com.example.travlediary.service.travelplan.TravelPlanPresenceDestinations;
import com.example.travlediary.service.travelplan.TravelPlanPresenceService;
import com.example.travlediary.service.travelplan.TravelPlanRoomAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * 접속 표시의 들어옴/나감 처리.
 *
 * <p>어느 방인지는 목적지에서 오지만, 그 방에 들어갈 자격은 서버가 다시 확인한다.
 * 클라이언트가 보내 온 memberId 나 userId 는 쓰지 않는다.
 */
@Controller
@RequiredArgsConstructor
public class TravelPlanPresenceController {

    private final TravelPlanRoomAccess travelPlanRoomAccess;
    private final TravelPlanPresenceService travelPlanPresenceService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    /**
     * 화면이 열린 뒤 보내는 첫 인사.
     * 자격을 확인하고 이 연결을 방에 올린 다음, 방 전체에 현재 접속 현황을 알린다.
     */
    @MessageMapping("/travel-plans/{travelPlanId}/presence/join")
    public void join(@DestinationVariable Long travelPlanId,
                     Principal principal,
                     SimpMessageHeaderAccessor headerAccessor) {
        Long memberId = travelPlanRoomAccess.findActiveMemberId(principal, travelPlanId)
                .orElseThrow(() -> new AccessDeniedException("여행계획에 참여 중이 아닙니다."));

        travelPlanPresenceService.join(travelPlanId, memberId, headerAccessor.getSessionId());
        broadcast(travelPlanId);
    }

    /**
     * 탭을 닫거나 연결이 끊어졌을 때.
     * 어느 방의 누구였는지는 서버가 들고 있던 연결 정보로만 알아낸다.
     * 모르는 연결이 들어와도 조용히 지나간다.
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        travelPlanPresenceService.leave(event.getSessionId()).ifPresent(this::broadcast);
    }

    private void broadcast(Long travelPlanId) {
        simpMessagingTemplate.convertAndSend(
                TravelPlanPresenceDestinations.topic(travelPlanId),
                TravelPlanPresenceDto.of(travelPlanPresenceService.onlineMemberIds(travelPlanId)));
    }
}
