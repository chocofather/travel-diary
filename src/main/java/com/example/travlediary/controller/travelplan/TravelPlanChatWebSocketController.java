package com.example.travlediary.controller.travelplan;

import com.example.travlediary.service.travelplan.TravelPlanChatDestinations;
import com.example.travlediary.service.travelplan.TravelPlanChatService;
import com.example.travlediary.service.travelplan.TravelPlanValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * 채팅 보내기 / 지우기 / 읽음 처리.
 *
 * <p>클라이언트가 보내는 것은 내용과 메시지 번호까지다.
 * 누가 보냈는지는 서버가 Principal 로 정한다. memberId / displayName 은 받지 않는다.
 *
 * <p>실패 사유는 방 전체가 아니라 보낸 사람에게만 돌려준다.
 */
@Controller
@RequiredArgsConstructor
public class TravelPlanChatWebSocketController {

    private final TravelPlanChatService travelPlanChatService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    /**
     * 새 메시지.
     * 저장이 끝나야 방으로 나간다. 여기서 직접 방에 보내지 않는다.
     */
    @MessageMapping("/travel-plans/{travelPlanId}/chat/send")
    public void send(@DestinationVariable Long travelPlanId,
                     @Payload Map<String, Object> payload,
                     Principal principal) {
        try {
            travelPlanChatService.sendMessage(principal, travelPlanId, text(payload.get("content")));
        } catch (TravelPlanValidationException | AccessDeniedException exception) {
            // 보낸 사람만 사유를 본다. 입력한 내용은 화면에 그대로 남는다.
            replyError(principal, "SEND_FAILED", exception.getMessage());
        }
    }

    /** 본인이 보낸 메시지 지움. 지워진 뒤의 알림도 Service 가 커밋 뒤에 내보낸다. */
    @MessageMapping("/travel-plans/{travelPlanId}/chat/delete")
    public void delete(@DestinationVariable Long travelPlanId,
                       @Payload Map<String, Object> payload,
                       Principal principal) {
        try {
            travelPlanChatService.deleteMessage(principal, travelPlanId,
                    number(payload.get("messageId")));
        } catch (TravelPlanValidationException | AccessDeniedException exception) {
            replyError(principal, "DELETE_FAILED", exception.getMessage());
        }
    }

    /**
     * 메시지에 반응 남기기 / 거두기.
     *
     * <p>화면이 보내는 것은 메시지 번호와 반응 종류까지다.
     * 누가 눌렀는지도, 지금 개수가 몇인지도 서버가 정한다.
     * 바뀐 뒤의 알림은 Service 가 커밋 뒤에 방으로 내보낸다.
     */
    @MessageMapping("/travel-plans/{travelPlanId}/chat/react")
    public void react(@DestinationVariable Long travelPlanId,
                      @Payload Map<String, Object> payload,
                      Principal principal) {
        try {
            travelPlanChatService.toggleReaction(principal, travelPlanId,
                    number(payload.get("messageId")), text(payload.get("reactionType")));
        } catch (TravelPlanValidationException | AccessDeniedException exception) {
            replyError(principal, "REACT_FAILED", exception.getMessage());
        }
    }

    /**
     * 여기까지 읽었다.
     * 갱신된 안 읽은 개수를 그 사람에게만 돌려주어 상단 배지를 맞춘다.
     */
    @MessageMapping("/travel-plans/{travelPlanId}/chat/read")
    public void read(@DestinationVariable Long travelPlanId,
                     @Payload Map<String, Object> payload,
                     Principal principal) {
        try {
            int unreadCount = travelPlanChatService.markRead(principal, travelPlanId,
                    number(payload.get("lastReadMessageId")));
            simpMessagingTemplate.convertAndSendToUser(principal.getName(),
                    TravelPlanChatDestinations.REPLY_QUEUE,
                    Map.of("type", "UNREAD", "unreadCount", unreadCount));
        } catch (AccessDeniedException exception) {
            replyError(principal, "READ_FAILED", exception.getMessage());
        }
    }

    private void replyError(Principal principal, String type, String message) {
        simpMessagingTemplate.convertAndSendToUser(principal.getName(),
                TravelPlanChatDestinations.REPLY_QUEUE,
                Map.of("type", type,
                        "message", message == null ? "처리하지 못했습니다." : message));
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
