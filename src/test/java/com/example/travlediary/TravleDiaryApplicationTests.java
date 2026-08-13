package com.example.travlediary;

import com.example.travlediary.service.email.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.mail.username=",
        "spring.mail.password="
})
class TravleDiaryApplicationTests {

    @Autowired private EmailService emailService;

    @Test
    void contextLoadsWithoutMailCredentials() {
        org.assertj.core.api.Assertions.assertThat(emailService).isNotNull();
    }

}
