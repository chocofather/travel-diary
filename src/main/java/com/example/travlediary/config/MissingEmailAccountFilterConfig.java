package com.example.travlediary.config;

import com.example.travlediary.security.MissingEmailAccountFilter;
import com.example.travlediary.service.user.MissingEmailRegistrationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 이메일 미등록 소셜 회원 접근 통제 필터 등록.
 * 이용제한·탈퇴 유예 필터와 같은 이유로 SecurityConfig 와 분리해
 * 웹 계층 테스트 슬라이스에는 올라오지 않는다.
 */
@Configuration
public class MissingEmailAccountFilterConfig {

    @Bean
    public MissingEmailAccountFilter missingEmailAccountFilter(
            MissingEmailRegistrationService missingEmailRegistrationService) {
        return new MissingEmailAccountFilter(missingEmailRegistrationService);
    }
}
