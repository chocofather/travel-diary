package com.example.travlediary.service.email;

import com.example.travlediary.config.MailAsyncConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(EmailDispatchServiceTest.TestConfig.class)
class EmailDispatchServiceTest {

    @Autowired private EmailDispatchService emailDispatchService;
    @Autowired private EmailService emailService;
    @Autowired @Qualifier(MailAsyncConfig.MAIL_EXECUTOR)
    private ThreadPoolTaskExecutor mailExecutor;

    @BeforeEach
    void resetMailMock() {
        reset(emailService);
    }

    @Test
    void smtpDeliveryRunsOutsideTheRequestThread() throws Exception {
        CountDownLatch smtpStarted = new CountDownLatch(1);
        CountDownLatch releaseSmtp = new CountDownLatch(1);
        doAnswer(invocation -> {
            smtpStarted.countDown();
            releaseSmtp.await(2, TimeUnit.SECONDS);
            return null;
        }).when(emailService).sendVerificationEmail(anyString(), anyString());

        assertTimeout(Duration.ofMillis(500), () ->
                emailDispatchService.dispatchVerificationEmail(
                        7L, "member@gmail.com", "safe-test-token"));
        assertThat(smtpStarted.await(1, TimeUnit.SECONDS)).isTrue();

        releaseSmtp.countDown();
        verify(emailService, timeout(2_000))
                .sendVerificationEmail("member@gmail.com", "safe-test-token");
    }

    @Test
    void asynchronousSmtpFailureDoesNotEscapeToTheCaller() {
        doThrow(new EmailDeliveryException("delivery failed"))
                .when(emailService).sendVerificationEmail(anyString(), anyString());

        assertDoesNotThrow(() -> emailDispatchService.dispatchVerificationEmail(
                7L, "member@gmail.com", "safe-test-token"));
        verify(emailService, timeout(2_000))
                .sendVerificationEmail("member@gmail.com", "safe-test-token");
    }

    @Test
    void passwordResetSmtpDeliveryRunsOutsideTheRequestThread() throws Exception {
        CountDownLatch smtpStarted = new CountDownLatch(1);
        CountDownLatch releaseSmtp = new CountDownLatch(1);
        doAnswer(invocation -> {
            smtpStarted.countDown();
            releaseSmtp.await(2, TimeUnit.SECONDS);
            return null;
        }).when(emailService).sendPasswordResetEmail(anyString(), anyString());

        assertTimeout(Duration.ofMillis(500), () ->
                emailDispatchService.dispatchPasswordResetEmail(
                        "member@gmail.com", "https://travel.example/reset"));
        assertThat(smtpStarted.await(1, TimeUnit.SECONDS)).isTrue();

        releaseSmtp.countDown();
        verify(emailService, timeout(2_000))
                .sendPasswordResetEmail(
                        "member@gmail.com", "https://travel.example/reset");
    }

    @Test
    void usernameRecoverySmtpFailureDoesNotEscapeToTheCaller() {
        doThrow(new EmailDeliveryException("delivery failed"))
                .when(emailService).sendUsernameRecoveryEmail(
                        anyString(), anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> emailDispatchService.dispatchUsernameRecoveryEmail(
                "member@gmail.com", "travel-member",
                "https://travel.example/login", "https://travel.example/find-password"));
        verify(emailService, timeout(2_000))
                .sendUsernameRecoveryEmail(
                        "member@gmail.com", "travel-member",
                        "https://travel.example/login", "https://travel.example/find-password");
    }

    @Test
    void dedicatedMailExecutorIsBoundedAndNamed() {
        assertThat(mailExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(mailExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(mailExecutor.getThreadNamePrefix()).isEqualTo("travel-diary-mail-");
        assertThat(mailExecutor.getThreadPoolExecutor().getQueue().remainingCapacity())
                .isEqualTo(100);
    }

    @Configuration
    @Import({MailAsyncConfig.class, EmailDispatchService.class})
    static class TestConfig {
        @Bean
        EmailService emailService() {
            return mock(EmailService.class);
        }
    }
}
