package com.example.travlediary.service.email;

import com.example.travlediary.config.i18n.SupportedLanguage;

/**
 * 메일 발송은 @Async 로 넘어가므로 언어를 LocaleContextHolder 에서 다시 읽지 않는다.
 * 요청 스레드에서 정해진 SupportedLanguage 를 인자로 받아 그대로 사용한다.
 */
public interface EmailService {
    void sendVerificationEmail(String to, String token, SupportedLanguage language);

    void sendUsernameRecoveryEmail(String to, String username, String loginUrl,
                                   String passwordResetUrl, SupportedLanguage language);

    void sendPasswordResetEmail(String to, String resetUrl, SupportedLanguage language);

    /** 탈퇴 유예 계정 복구 링크. 유효시간은 계정의 남은 유예기간에 따라 30분보다 짧아질 수 있다. */
    void sendAccountRecoveryEmail(String to, String recoveryUrl, long validMinutes,
                                  SupportedLanguage language);

    void sendEmail(String to, String subject, String htmlContent);

}
