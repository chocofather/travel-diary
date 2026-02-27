package com.example.travlediary.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
public class WebSecurityIgnoreConfig {

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                .requestMatchers(
                        "/test-map.html",     // 정적 테스트 페이지
                        "/favicon.ico",
                        "/v2/maps/sdk.js",    // Kakao 지도 SDK (내부 경로 프록시 대비)
                        "/resources/**",      // 정적 리소스 루트
                        "/error"              // 에러 페이지 (Spring 기본)
                );
    }
}
