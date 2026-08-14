package com.example.travlediary.config;

import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.RestrictedAccountFilter;
import com.example.travlediary.service.user.UserSanctionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 이용제한 접근 통제 필터 등록.
 * SecurityConfig 와 분리해 두어 웹 계층 테스트 슬라이스에는 올라오지 않는다.
 */
@Configuration
public class RestrictedAccountFilterConfig {

    @Bean
    public RestrictedAccountFilter restrictedAccountFilter(UserMapper userMapper,
                                                           UserSanctionService userSanctionService) {
        return new RestrictedAccountFilter(userMapper, userSanctionService);
    }
}
