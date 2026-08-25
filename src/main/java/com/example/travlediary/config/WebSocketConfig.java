package com.example.travlediary.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

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

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 서버 -> 클라이언트 브로드캐스트는 /topic 아래로만 나간다.
        registry.enableSimpleBroker("/topic");
        // 클라이언트 -> 서버 메시지는 /app 으로 들어와 @MessageMapping 이 받는다.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 구독 자체를 막아야 다른 방의 접속 정보가 새지 않는다.
        registration.interceptors(travelPlanWebSocketAuthInterceptor);
    }
}
