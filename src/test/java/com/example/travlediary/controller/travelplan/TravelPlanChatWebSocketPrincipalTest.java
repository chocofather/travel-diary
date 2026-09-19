package com.example.travlediary.controller.travelplan;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.travelplan.TravelPlanChatDestinations;
import com.example.travlediary.service.travelplan.TravelPlanChatService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelPlanChatWebSocketPrincipalTest {

    @Test
    void personalQueueUsesTheStableUserIdPrincipalName() {
        TravelPlanChatService chatService = mock(TravelPlanChatService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        TravelPlanChatWebSocketController controller =
                new TravelPlanChatWebSocketController(chatService, messagingTemplate);
        User user = new User();
        user.setId(41L);
        user.setUserRole(UserRole.USER);
        user.setUserPassword("encoded");
        CustomUserDetails details = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken principal =
                UsernamePasswordAuthenticationToken.authenticated(
                        details, null, details.getAuthorities());
        when(chatService.markRead(principal, 9L, 12L)).thenReturn(3);

        controller.read(9L, Map.of("lastReadMessageId", 12L), principal);

        verify(messagingTemplate).convertAndSendToUser(
                "user:41", TravelPlanChatDestinations.REPLY_QUEUE,
                Map.of("type", "UNREAD", "unreadCount", 3));
    }
}
