package com.example.travlediary.service.email;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.service.user.EmailPolicy;
import com.example.travlediary.service.user.UserService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class EmailServiceImpl implements EmailService {

    static final String VERIFICATION_SUBJECT_CODE = "mail.verification.subject";
    static final String USERNAME_RECOVERY_SUBJECT_CODE = "mail.usernameRecovery.subject";
    static final String PASSWORD_RESET_SUBJECT_CODE = "mail.passwordReset.subject";
    static final String ACCOUNT_RECOVERY_SUBJECT_CODE = "mail.accountRecovery.subject";
    private static final String SENDER_NAME = "Travel Diary";
    private static final String MISSING_CONFIGURATION_MESSAGE =
            "메일 발송 설정이 구성되지 않았습니다. MAIL_USERNAME / MAIL_PASSWORD 환경변수를 확인하세요.";
    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MessageSource messageSource;
    private final String fromAddress;
    private final String mailPassword;
    private final String serverUrl;

    public EmailServiceImpl(JavaMailSender mailSender,
                            TemplateEngine templateEngine,
                            MessageSource messageSource,
                            @Value("${spring.mail.username:}") String fromAddress,
                            @Value("${spring.mail.password:}") String mailPassword,
                            @Value("${custom.server-url}") String serverUrl) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.messageSource = messageSource;
        this.fromAddress = fromAddress;
        this.mailPassword = mailPassword;
        this.serverUrl = serverUrl;
    }

    @Override
    public void sendVerificationEmail(String to, String token, SupportedLanguage language) {
        Context context = mailContext(language);
        context.setVariable("verificationUrl", buildVerificationUrl(token));
        // 만료시간은 정책 상수에서 가져와 문구와 실제 정책이 어긋나지 않게 한다.
        context.setVariable("validHours", EmailVerificationService.TOKEN_VALIDITY.toHours());

        send(to, VERIFICATION_SUBJECT_CODE, "email/verification-email", context, language);
    }

    @Override
    public void sendUsernameRecoveryEmail(String to, String username, String loginUrl,
                                          String passwordResetUrl, SupportedLanguage language) {
        SupportedLanguage resolved = resolve(language);
        Context context = mailContext(resolved);
        context.setVariable("username", username);
        context.setVariable("loginUrl", loginUrl);
        context.setVariable("passwordResetUrl", passwordResetUrl);

        // 이 메일은 주소가 링크 안에만 있어 평문에서는 보이지 않으므로 따로 덧붙인다.
        String plainTextLinks = "%n%n%s: %s%n%s: %s".formatted(
                message("mail.usernameRecovery.loginButton", resolved), loginUrl,
                message("mail.usernameRecovery.resetLink", resolved), passwordResetUrl);

        send(to, USERNAME_RECOVERY_SUBJECT_CODE, "email/username-recovery-email",
                context, resolved, plainTextLinks);
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetUrl, SupportedLanguage language) {
        Context context = mailContext(language);
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("validMinutes", UserService.RESET_TOKEN_VALIDITY.toMinutes());

        send(to, PASSWORD_RESET_SUBJECT_CODE, "email/password-reset-email", context, language);
    }

    @Override
    public void sendAccountRecoveryEmail(String to, String recoveryUrl, long validMinutes,
                                         SupportedLanguage language) {
        Context context = mailContext(language);
        context.setVariable("recoveryUrl", recoveryUrl);
        // 남은 유예기간이 짧으면 30분보다 짧아지므로 계산된 값을 그대로 안내한다.
        context.setVariable("validMinutes", validMinutes);

        send(to, ACCOUNT_RECOVERY_SUBJECT_CODE, "email/account-recovery-email", context, language);
    }

    /** 지원 언어가 아니면 사이트 기본값과 같은 한국어로 떨어진다. */
    private SupportedLanguage resolve(SupportedLanguage language) {
        return language == null ? SupportedLanguage.KOREAN : language;
    }

    private Context mailContext(SupportedLanguage language) {
        SupportedLanguage resolved = resolve(language);
        Context context = new Context(resolved.getLocale());
        context.setVariable("mailLanguageTag", resolved.getLanguageTag());
        return context;
    }

    private String message(String code, SupportedLanguage language) {
        return messageSource.getMessage(code, null, resolve(language).getLocale());
    }

    /** 제목과 본문을 같은 언어로 렌더링한다. 평문 파트는 번역된 HTML 에서 뽑아 항상 같은 언어가 된다. */
    private void send(String to, String subjectCode, String template,
                      Context context, SupportedLanguage language, String... plainTextExtras) {
        String html = templateEngine.process(template, context);
        String plainText = Jsoup.parse(html).text() + String.join("", plainTextExtras);
        sendMimeMessage(to, message(subjectCode, language), plainText, html);
    }

    @Override
    public void sendEmail(String to, String subject, String htmlContent) {
        sendMimeMessage(to, subject, Jsoup.parse(htmlContent).text(), htmlContent);
    }

    String buildVerificationUrl(String token) {
        return UriComponentsBuilder.fromUriString(serverUrl)
                .path("/users/verify")
                .queryParam("token", token)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    private void sendMimeMessage(String to, String subject, String plainText, String htmlContent) {
        ensureMailConfigured();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(fromAddress, SENDER_NAME);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText, htmlContent);

            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
            log.error("Email delivery failed: exceptionType={}",
                    exception.getClass().getSimpleName());
            throw new EmailDeliveryException(
                    "이메일 발송을 완료할 수 없습니다. 수신자=" + EmailPolicy.mask(to),
                    exception);
        }
    }

    private void ensureMailConfigured() {
        if (fromAddress == null || fromAddress.isBlank()
                || mailPassword == null || mailPassword.isBlank()) {
            log.error(MISSING_CONFIGURATION_MESSAGE);
            throw new EmailDeliveryException(MISSING_CONFIGURATION_MESSAGE);
        }
    }
}
