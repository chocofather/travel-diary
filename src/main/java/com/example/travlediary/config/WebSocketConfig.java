package com.example.travlediary.config;

import com.example.travlediary.service.travelplan.TravelPlanRoomSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * 공동 여행계획의 실시간 접속 표시용 STOMP 설정.
 *
 * <p>핸드셰이크는 평범한 HTTP 요청이라 SecurityConfig 의 인증을 그대로 지나간다.
 * 그래서 로그인 세션의 Authentication 이 그대로 STOMP Principal 이 되고,
 * 클라이언트가 자기 신분을 보내 올 필요가 없다.
 *
 * <p>지금은 단일 서버 기준이라 브로커도 메모리 브로커 하나만 쓴다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** 브라우저가 붙는 STOMP 엔드포인트. SockJS 폴백은 두지 않는다. */
    public static final String ENDPOINT = "/ws";

    private final TravelPlanWebSocketAuthInterceptor travelPlanWebSocketAuthInterceptor;
    private final TravelPlanRoomSessionRegistry travelPlanRoomSessionRegistry;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT);
    }

    /**
     * 연결 하나하나를 장부에 올려 둔다.
     *
     * <p>구독은 SUBSCRIBE 한 번만 검사되므로, 방에서 빠진 사람의 연결은
     * 서버가 직접 끊어야 그때부터의 내용이 더 가지 않는다.
     * 끊으려면 연결 객체를 들고 있어야 해서 여기서 받아 둔다.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                travelPlanRoomSessionRegistry.register(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
                    throws Exception {
                // 스스로 나갔든 우리가 끊었든 한 곳에서 정리한다.
                travelPlanRoomSessionRegistry.forget(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 방 전체 알림은 /topic, 요청한 사람에게만 가는 답은 /queue 로 나간다.
        // /user/queue/... 는 브로커가 /queue 를 맡고 있을 때만 전달되므로 둘 다 등록한다.
        registry.enableSimpleBroker("/topic", "/queue");
        // 클라이언트 -> 서버 메시지는 /app 으로 들어와 @MessageMapping 이 받는다.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 구독 자체를 막아야 다른 방의 접속 정보가 새지 않는다.
        registration.interceptors(travelPlanWebSocketAuthInterceptor);
    }
}
