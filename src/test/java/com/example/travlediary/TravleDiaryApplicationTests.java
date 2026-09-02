package com.example.travlediary;

import com.example.travlediary.service.email.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.mail.username=",
        "spring.mail.password=",
        "spring.security.oauth2.client.registration.google.client-id=test-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
        "spring.security.oauth2.client.registration.kakao.client-id=test-kakao-client",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-kakao-secret",
        "spring.security.oauth2.client.registration.naver.client-id=test-naver-client",
        "spring.security.oauth2.client.registration.naver.client-secret=test-naver-secret"
})
class TravleDiaryApplicationTests {

    @Autowired private EmailService emailService;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void contextLoadsWithoutMailCredentials() {
        org.assertj.core.api.Assertions.assertThat(emailService).isNotNull();
    }

}
