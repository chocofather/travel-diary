package com.example.travlediary.config;

import com.example.travlediary.security.ClientIpFilter;
import com.example.travlediary.security.ClientIpResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 클라이언트 주소 판별 설정.
 *
 * <p>기본값은 {@link ClientIpResolver.Mode#DIRECT} 다. 로컬 개발과, 프록시 없이 직접 여는
 * 운영 모두에서 전달 머리말을 전혀 믿지 않는다. 프록시 뒤에 둘 때만 환경변수로 켠다.
 *
 * <p>SecurityConfig 와 분리해 두어 웹 계층 테스트 슬라이스에는 올라오지 않는다.
 * 슬라이스에서는 필터가 없으니 접속 주소를 그대로 쓰게 되고, 그것이 DIRECT 와 같은 동작이다.
 */
@Configuration
public class ClientIpConfig {

    private static final Logger log = LoggerFactory.getLogger(ClientIpConfig.class);

    @Bean
    public ClientIpResolver clientIpResolver(
            @Value("${custom.client-ip.mode:DIRECT}") ClientIpResolver.Mode mode,
            @Value("${custom.client-ip.trusted-proxies:}") String trustedProxies) {
        // 어떤 정책으로 떴는지는 한 번만 남긴다. 주소 목록은 설정 값 그대로라 비밀이 아니다.
        log.info("Client IP resolution mode: mode={}, trustedProxies={}",
                mode, trustedProxies.isBlank() ? "<none>" : trustedProxies);
        return new ClientIpResolver(mode, trustedProxies);
    }

    /**
     * 주소 판별은 다른 어떤 판단보다 먼저 끝나야 한다. 로그인 제한 필터도 이 값을 읽는다.
     */
    @Bean
    public FilterRegistrationBean<ClientIpFilter> clientIpFilterRegistration(
            ClientIpResolver clientIpResolver) {
        FilterRegistrationBean<ClientIpFilter> registration =
                new FilterRegistrationBean<>(new ClientIpFilter(clientIpResolver));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
