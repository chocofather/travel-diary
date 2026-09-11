package com.example.travlediary.service.email;

import com.example.travlediary.config.MailAsyncConfig;
import com.example.travlediary.config.i18n.SupportedLanguage;
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
    public void dispatchVerificationEmail(Long userId, String recipient, String token,
                                          SupportedLanguage language) {
        try {
            emailService.sendVerificationEmail(recipient, token, language);
        } catch (RuntimeException exception) {
            log.error("Asynchronous verification email delivery failed: "
                            + "userId={}, recipient={}, exceptionType={}",
                    userId, EmailPolicy.mask(recipient), exception.getClass().getSimpleName());
        }
    }

    /** 탈퇴 유예 계정의 복구 링크. 원문 토큰이 링크 안에 있으므로 로그로 남기지 않는다. */
    @Async(MailAsyncConfig.MAIL_EXECUTOR)
    public void dispatchAccountRecoveryEmail(String recipient,
                                             String recoveryUrl,
                                             long validMinutes,
                                             SupportedLanguage language) {
        try {
            emailService.sendAccountRecoveryEmail(
                    recipient, recoveryUrl, validMinutes, language);
        } catch (RuntimeException exception) {
            log.error("Asynchronous account recovery email delivery failed: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    @Async(MailAsyncConfig.MAIL_EXECUTOR)
    public void dispatchUsernameRecoveryEmail(String recipient,
                                              String username,
                                              String loginUrl,
                                              String passwordResetUrl,
                                              SupportedLanguage language) {
        try {
            emailService.sendUsernameRecoveryEmail(
                    recipient, username, loginUrl, passwordResetUrl, language);
        } catch (RuntimeException exception) {
            log.error("Asynchronous username recovery email delivery failed: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    @Async(MailAsyncConfig.MAIL_EXECUTOR)
    public void dispatchPasswordResetEmail(String recipient, String resetUrl,
                                           SupportedLanguage language) {
        try {
            emailService.sendPasswordResetEmail(recipient, resetUrl, language);
        } catch (RuntimeException exception) {
            log.error("Asynchronous password reset email delivery failed: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
