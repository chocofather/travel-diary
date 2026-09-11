package com.example.travlediary.config;

import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.WithdrawalPendingAccountFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 탈퇴 유예 접근 통제 필터 등록.
 * 이용제한 필터와 같은 이유로 SecurityConfig 와 분리해 웹 계층 테스트 슬라이스에는 올라오지 않는다.
 */
@Configuration
public class WithdrawalPendingAccountFilterConfig {

    @Bean
    public WithdrawalPendingAccountFilter withdrawalPendingAccountFilter(UserMapper userMapper) {
        return new WithdrawalPendingAccountFilter(userMapper);
    }
}
