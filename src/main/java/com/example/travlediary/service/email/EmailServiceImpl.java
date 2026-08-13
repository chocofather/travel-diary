package com.example.travlediary.service.email;

import com.example.travlediary.service.user.EmailPolicy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    static final String VERIFICATION_SUBJECT = "[Travel Diary] 이메일 인증을 완료해주세요";
    static final String USERNAME_RECOVERY_SUBJECT = "[Travel Diary] 아이디를 안내해 드려요";
    static final String PASSWORD_RESET_SUBJECT = "[Travel Diary] 비밀번호를 재설정해 주세요";
    private static final String SENDER_NAME = "Travel Diary";
    private static final String MISSING_CONFIGURATION_MESSAGE =
            "메일 발송 설정이 구성되지 않았습니다. MAIL_USERNAME / MAIL_PASSWORD 환경변수를 확인하세요.";
    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromAddress;
    private final String mailPassword;
    private final String serverUrl;

    public EmailServiceImpl(JavaMailSender mailSender,
                            TemplateEngine templateEngine,
                            @Value("${spring.mail.username:}") String fromAddress,
                            @Value("${spring.mail.password:}") String mailPassword,
                            @Value("${custom.server-url}") String serverUrl) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
        this.mailPassword = mailPassword;
        this.serverUrl = serverUrl;
    }

    @Override
    public void sendVerificationEmail(String to, String token) {
        String verificationUrl = buildVerificationUrl(token);
        Context context = new Context(Locale.KOREAN);
        context.setVariable("verificationUrl", verificationUrl);
        context.setVariable("validHours", EmailVerificationService.TOKEN_VALIDITY.toHours());

        String html = templateEngine.process("email/verification-email", context);
        String plainText = """
                Travel Diary 이메일 인증

                Travel Diary 회원가입을 위해 이메일 주소 확인이 필요합니다.
                아래 주소를 브라우저에 붙여넣어 인증을 완료해주세요.

                %s

                이 링크는 24시간 동안 유효합니다.
                본인이 회원가입을 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(verificationUrl);

        sendMimeMessage(to, VERIFICATION_SUBJECT, plainText, html);
    }

    @Override
    public void sendUsernameRecoveryEmail(String to, String username,
                                          String loginUrl, String passwordResetUrl) {
        Context context = new Context(Locale.KOREAN);
        context.setVariable("username", username);
        context.setVariable("loginUrl", loginUrl);
        context.setVariable("passwordResetUrl", passwordResetUrl);

        String html = templateEngine.process("email/username-recovery-email", context);
        String plainText = """
                Travel Diary 아이디 안내

                요청하신 계정의 아이디를 안내해 드립니다.
                아이디: %s

                로그인: %s
                비밀번호 재설정: %s

                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(username, loginUrl, passwordResetUrl);

        sendMimeMessage(to, USERNAME_RECOVERY_SUBJECT, plainText, html);
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetUrl) {
        Context context = new Context(Locale.KOREAN);
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("validMinutes", 30);

        String html = templateEngine.process("email/password-reset-email", context);
        String plainText = """
                Travel Diary 비밀번호 재설정

                비밀번호 재설정 요청을 받았습니다.
                아래 주소에서 30분 이내에 새 비밀번호를 설정해주세요.

                %s

                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(resetUrl);

        sendMimeMessage(to, PASSWORD_RESET_SUBJECT, plainText, html);
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
