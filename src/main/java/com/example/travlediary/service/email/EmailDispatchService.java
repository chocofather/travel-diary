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

    @Async(MailAsyncConfig.MAIL_EXECUTOR)
    public void dispatchAccountRecoveryEmail(String recipient,
                                             String subject,
                                             String htmlContent) {
        try {
            emailService.sendEmail(recipient, subject, htmlContent);
        } catch (RuntimeException exception) {
            log.error("Asynchronous account recovery email delivery failed: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    @Async(MailAsyncConfig.MAIL_EXECUTOR)
    public void dispatchUsernameRecoveryEmail(String recipient,
                                              String username,
                                              String loginUrl,
                                              String passwordResetUrl) {
        try {
            emailService.sendUsernameRecoveryEmail(
                    recipient, username, loginUrl, passwordResetUrl);
        } catch (RuntimeException exception) {
            log.error("Asynchronous username recovery email delivery failed: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    @Async(MailAsyncConfig.MAIL_EXECUTOR)
    public void dispatchPasswordResetEmail(String recipient, String resetUrl) {
        try {
            emailService.sendPasswordResetEmail(recipient, resetUrl);
        } catch (RuntimeException exception) {
            log.error("Asynchronous password reset email delivery failed: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
