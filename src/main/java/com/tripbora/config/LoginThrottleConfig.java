package com.tripbora.config;

import com.tripbora.security.LoginThrottle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoginThrottleConfig {

    @Bean
    public LoginThrottle loginThrottle() {
        return new LoginThrottle();
    }
}
