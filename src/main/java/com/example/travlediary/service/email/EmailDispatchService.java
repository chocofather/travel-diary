package com.example.travlediary.service.email;

import com.example.travlediary.config.MailAsyncConfig;
import com.example.travlediary.service.user.EmailPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailDispatchService {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchService.class);

    private final EmailService emailService;

    public EmailDispatchService(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async(MailAsyncConfig.MAIL_EXECUTOR)
    public void dispatchVerificationEmail(Long userId, String recipient, String token) {
        try {
            emailService.sendVerificationEmail(recipient, token);
        } catch (RuntimeException exception) {
            log.error("Asynchronous verification email delivery failed: "
                            + "userId={}, recipient={}, exceptionType={}",
                    userId, EmailPolicy.mask(recipient), exception.getClass().getSimpleName());
        }
    }
}
