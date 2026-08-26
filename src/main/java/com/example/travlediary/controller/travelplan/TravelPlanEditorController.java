package com.example.travlediary.controller.travelplan;

import com.example.travlediary.dto.TravelPlanEditorEventDto;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.service.travelplan.TravelPlanEditorDestinations;
import com.example.travlediary.service.travelplan.TravelPlanEditorRealtimeService;
import com.example.travlediary.service.travelplan.TravelPlanEditorRealtimeService.EditorLock;
import com.example.travlediary.service.travelplan.TravelPlanRoomAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * 작성 중 상태(자리 잡기 / 임시 내용)를 다룬다.
 *
 * <p>여기서 DB 에 쓰는 것은 없다. 저장은 기존 HTTP 경로 그대로다.
 * 어느 방·어느 자리인지는 목적지에서 오지만, 그 자리를 다룰 자격은 서버가 매번 다시 본다.
 * 클라이언트가 보낸 memberId / displayName 은 쓰지 않는다.
 */
@Controller
@RequiredArgsConstructor
public class TravelPlanEditorController {

    private final TravelPlanRoomAccess travelPlanRoomAccess;
    private final TravelPlanEditorRealtimeService travelPlanEditorRealtimeService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    /**
     * 편집을 시작하기 전에 그 자리를 잡는다.
     * 결과는 요청한 사람에게만 돌려주어, 잡힌 사람만 편집기를 열 수 있게 한다.
     */
    @MessageMapping("/travel-plans/{travelPlanId}/editor/lock")
    public void lock(@DestinationVariable Long travelPlanId,
                     @Payload Map<String, Object> payload,
                     Principal principal,
                     SimpMessageHeaderAccessor headerAccessor) {
        TravelPlanMember member = requireMember(principal, travelPlanId);
        String requestId = text(payload.get("requestId"));
        Long dayId = number(payload.get("dayId"));
        Long itemId = number(payload.get("itemId"));
        Long alternativeId = number(payload.get("alternativeId"));
        boolean alternative = isAlternativeMode(text(payload.get("mode")));

        // 다른 방의 dayId / itemId / alternativeId 를 섞어 보내도 여기서 걸린다.
        // 새 대안 자리는 이미 B/C 가 다 찼는지도 서버가 본다.
        boolean allowed = alternative
                ? travelPlanRoomAccess.isEditableAlternativeSpot(
                        travelPlanId, dayId, itemId, alternativeId)
                : travelPlanRoomAccess.isEditableSpot(travelPlanId, dayId, itemId);
        if (!allowed) {
            throw new AccessDeniedException("편집할 수 없는 자리입니다.");
        }

        EditorLock request = new EditorLock(
                travelPlanId,
                lockKeyOf(alternative, dayId, itemId, alternativeId),
                headerAccessor.getSessionId(),
                modeOf(alternative, itemId, alternativeId),
                dayId, itemId, alternative ? alternativeId : null,
                member.getId(), member.getDisplayName(), "", "");

        // 이 연결이 쓰던 다른 자리는 새 자리를 잡을 때 함께 놓인다.
        // 놓인 자리도 알려야 다른 화면의 "편집 중" 표시가 사라진다.
        travelPlanEditorRealtimeService
                .releasedWhenAcquiring(travelPlanId, request.sessionId(), request.lockKey())
                .forEach(released -> broadcast(travelPlanId,
                        TravelPlanEditorEventDto.unlocked(released.toDto())));

        Optional<EditorLock> acquired = travelPlanEditorRealtimeService.tryAcquire(request);
        // 요청한 사람에게만 성공/실패를 알린다. "아마 됐겠지" 로 편집기를 열지 않게 한다.
        simpMessagingTemplate.convertAndSendToUser(principal.getName(),
                TravelPlanEditorDestinations.LOCK_REPLY_QUEUE,
                TravelPlanEditorEventDto.lockResult(requestId, acquired.isPresent(),
                        acquired.map(EditorLock::toDto)
                                .orElseGet(() -> travelPlanEditorRealtimeService
                                        .find(travelPlanId, request.lockKey())
                                        .map(EditorLock::toDto)
                                        .orElse(null))));

        acquired.ifPresent(lock -> broadcast(travelPlanId,
                TravelPlanEditorEventDto.locked(lock.toDto())));
    }

    /** 작성 중 내용. 그 자리를 붙잡고 있는 연결만 보낼 수 있다. */
    @MessageMapping("/travel-plans/{travelPlanId}/editor/draft")
    public void draft(@DestinationVariable Long travelPlanId,
                      @Payload Map<String, Object> payload,
                      Principal principal,
                      SimpMessageHeaderAccessor headerAccessor) {
        requireMember(principal, travelPlanId);

        // 대안은 조건과 내용이 늘 함께 온다. 상대 화면이 두 칸을 같은 시점 값으로 본다.
        travelPlanEditorRealtimeService.updateDraft(
                        travelPlanId,
                        text(payload.get("lockKey")),
                        headerAccessor.getSessionId(),
                        text(payload.get("conditionLabel")),
                        text(payload.get("content")))
                .ifPresent(lock -> broadcast(travelPlanId,
                        TravelPlanEditorEventDto.draft(lock.toDto())));
    }

    /** 저장했거나 취소했다. 자리를 놓고 방에 알린다. */
    @MessageMapping("/travel-plans/{travelPlanId}/editor/unlock")
    public void unlock(@DestinationVariable Long travelPlanId,
                       @Payload Map<String, Object> payload,
                       Principal principal,
                       SimpMessageHeaderAccessor headerAccessor) {
        requireMember(principal, travelPlanId);

        travelPlanEditorRealtimeService.release(
                        travelPlanId, text(payload.get("lockKey")), headerAccessor.getSessionId())
                .ifPresent(lock -> broadcast(travelPlanId,
                        TravelPlanEditorEventDto.unlocked(lock.toDto())));
    }

    /**
     * 끊겼다 다시 붙은 화면이 지금 상태를 한 번에 받아 간다.
     * 사라진 옛 표시가 화면에 남지 않게 한다.
     */
    @MessageMapping("/travel-plans/{travelPlanId}/editor/sync")
    public void sync(@DestinationVariable Long travelPlanId, Principal principal) {
        requireMember(principal, travelPlanId);

        simpMessagingTemplate.convertAndSendToUser(principal.getName(),
                TravelPlanEditorDestinations.LOCK_REPLY_QUEUE,
                TravelPlanEditorEventDto.snapshot(
                        travelPlanEditorRealtimeService.locksOf(travelPlanId)));
    }

    /**
     * 탭을 닫거나 연결이 끊어졌다.
     * 그 연결이 붙잡고 있던 자리를 모두 놓아 다른 화면의 "편집 중" 표시가 사라지게 한다.
     * 접속 표시(presence)의 연결 처리와는 별개다.
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        travelPlanEditorRealtimeService.releaseAllBySession(event.getSessionId())
                .forEach(lock -> broadcast(lock.travelPlanId(),
                        TravelPlanEditorEventDto.unlocked(lock.toDto())));
    }

    /** 대안 자리인지. 클라이언트가 알려 주는 것은 "어떤 종류의 자리인가" 까지다. */
    private boolean isAlternativeMode(String mode) {
        return TravelPlanEditorRealtimeService.ALT_ADD_MODE.equals(mode)
                || TravelPlanEditorRealtimeService.ALT_EDIT_MODE.equals(mode);
    }

    /**
     * 자리 이름. A 일정과 대안이 섞이지 않게 종류마다 다른 이름을 쓴다.
     * B 인지 C 인지는 저장할 때 서버가 정하므로 새 대안 자리는 A 일정 하나당 하나다.
     */
    private String lockKeyOf(boolean alternative, Long dayId, Long itemId, Long alternativeId) {
        if (alternative) {
            return alternativeId != null
                    ? TravelPlanEditorRealtimeService.alternativeEditLockKey(alternativeId)
                    : TravelPlanEditorRealtimeService.alternativeAddLockKey(itemId);
        }
        return itemId != null
                ? TravelPlanEditorRealtimeService.editLockKey(itemId)
                : TravelPlanEditorRealtimeService.addLockKey(dayId);
    }

    private String modeOf(boolean alternative, Long itemId, Long alternativeId) {
        if (alternative) {
            return alternativeId != null
                    ? TravelPlanEditorRealtimeService.ALT_EDIT_MODE
                    : TravelPlanEditorRealtimeService.ALT_ADD_MODE;
        }
        return itemId != null
                ? TravelPlanEditorRealtimeService.EDIT_MODE
                : TravelPlanEditorRealtimeService.ADD_MODE;
    }

    private TravelPlanMember requireMember(Principal principal, Long travelPlanId) {
        return travelPlanRoomAccess.findActiveMember(principal, travelPlanId)
                .orElseThrow(() -> new AccessDeniedException("여행계획에 참여 중이 아닙니다."));
    }

    private void broadcast(Long travelPlanId, TravelPlanEditorEventDto event) {
        simpMessagingTemplate.convertAndSend(
                TravelPlanEditorDestinations.topic(travelPlanId), event);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long number(Object value) {
        if (value instanceof Number numeric) {
            return numeric.longValue();
        }
        if (value instanceof String raw && !raw.isBlank()) {
            try {
                return Long.valueOf(raw.trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }
}
