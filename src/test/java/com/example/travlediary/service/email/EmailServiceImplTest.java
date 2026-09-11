package com.example.travlediary.service.email;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.service.user.AccountRecoveryService;
import com.example.travlediary.service.user.UserService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock private JavaMailSender mailSender;

    private MimeMessage message;
    private SpringTemplateEngine templateEngine;
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        message = new MimeMessage(Session.getInstance(new Properties()));
        lenient().when(mailSender.createMimeMessage()).thenReturn(message);

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");

        // 메일 문구도 화면과 같은 messages 번들에서 온다.
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messages.setFallbackToSystemLocale(false);

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        templateEngine.setTemplateEngineMessageSource(messages);
        emailService = new EmailServiceImpl(
                mailSender,
                templateEngine,
                messages,
                "mailer@gmail.com",
                "test-app-password",
                "https://travel.example");
    }

    @Test
    void verificationMailHasExplicitSenderRecipientSubjectAndTextAlternatives() throws Exception {
        emailService.sendVerificationEmail(
                "member@gmail.com", "safe-token", SupportedLanguage.KOREAN);

        verify(mailSender).send(message);
        message.saveChanges();
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("mailer@gmail.com");
        assertThat(from.getPersonal()).isEqualTo("Travel Diary");
        assertThat(((InternetAddress) message.getRecipients(Message.RecipientType.TO)[0]).getAddress())
                .isEqualTo("member@gmail.com");
        assertThat(message.getSubject()).isEqualTo("[Travel Diary] 이메일 인증을 완료해주세요");

        MailBodies bodies = mailBodies(message.getContent());
        assertThat(bodies.plainText())
                .contains("Travel Diary 이메일 인증")
                .contains("https://travel.example/users/verify?token=safe-token")
                .contains("24시간 동안 유효")
                .contains("요청하지 않았다면");
        assertThat(bodies.html())
                .contains("Travel Diary")
                .contains("이메일 인증하기")
                .contains("https://travel.example/users/verify?token=safe-token")
                .contains("24시간 동안 유효")
                .contains("요청하지 않았다면");
    }

    @Test
    void usernameRecoveryMailContainsTheFullUsernameAndServiceLinks() throws Exception {
        emailService.sendUsernameRecoveryEmail(
                "member@gmail.com",
                "travel-member",
                "https://travel.example/login",
                "https://travel.example/users/find-password",
                SupportedLanguage.KOREAN);

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getSubject())
                .isEqualTo("[Travel Diary] 아이디를 안내해 드려요")
                .doesNotContain("travel-member");

        MailBodies bodies = mailBodies(message.getContent());
        assertThat(bodies.plainText())
                .contains("travel-member")
                .contains("https://travel.example/login")
                .contains("https://travel.example/users/find-password");
        assertThat(bodies.html())
                .contains("Travel Diary", "아이디를 안내해 드려요", "travel-member")
                .contains("로그인하기", "비밀번호 재설정")
                .contains("https://travel.example/login")
                .contains("https://travel.example/users/find-password");
    }

    @Test
    void passwordResetMailKeepsTheResetUrlAndThirtyMinuteSecurityGuidance()
            throws Exception {
        String resetUrl = "https://travel.example/users/reset-password?token=safe-token";

        emailService.sendPasswordResetEmail(
                "member@gmail.com", resetUrl, SupportedLanguage.KOREAN);

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getSubject())
                .isEqualTo("[Travel Diary] 비밀번호를 재설정해 주세요")
                .doesNotContain("safe-token");

        MailBodies bodies = mailBodies(message.getContent());
        assertThat(bodies.plainText())
                .contains(resetUrl, "30분", "요청하지 않았다면");
        assertThat(bodies.html())
                .contains("Travel Diary", "비밀번호를 재설정해 주세요")
                .contains("비밀번호 재설정", resetUrl, "30분", "요청하지 않았다면");
    }

    /* ---------- 5개 언어 계약 ---------- */

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "KOREAN              | [Travel Diary] 이메일 인증을 완료해주세요        | 이메일 인증하기   | 24시간",
            "ENGLISH             | [Travel Diary] Please verify your email        | Verify email    | 24 hours",
            "JAPANESE            | [Travel Diary] メール認証を完了してください        | メールを認証する  | 24時間",
            "CHINESE_SIMPLIFIED  | [Travel Diary] 请完成邮箱验证                    | 验证邮箱         | 24 小时",
            "CHINESE_TRADITIONAL | [Travel Diary] 請完成電子郵件驗證                 | 驗證電子郵件      | 24 小時"
    })
    void verificationMailFollowsTheRequestedLanguage(SupportedLanguage language, String subject,
                                                     String button, String validity)
            throws Exception {
        emailService.sendVerificationEmail("member@gmail.com", "safe-token", language);

        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo(subject);
        MailBodies bodies = mailBodies(message.getContent());
        assertThat(bodies.html())
                .contains(button, validity, "Travel Diary")
                .contains("https://travel.example/users/verify?token=safe-token")
                .doesNotContain("??").doesNotContain("{0}");
        assertThat(bodies.plainText()).contains(button, validity);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "KOREAN              | [Travel Diary] 비밀번호를 재설정해 주세요      | 30분",
            "ENGLISH             | [Travel Diary] Reset your password           | 30 minutes",
            "JAPANESE            | [Travel Diary] パスワードを再設定してください    | 30分",
            "CHINESE_SIMPLIFIED  | [Travel Diary] 请重置你的密码                  | 30 分钟",
            "CHINESE_TRADITIONAL | [Travel Diary] 請重設你的密碼                  | 30 分鐘"
    })
    void passwordResetMailFollowsTheRequestedLanguage(SupportedLanguage language, String subject,
                                                      String validity) throws Exception {
        String resetUrl = "https://travel.example/users/reset-password?token=safe-token";

        emailService.sendPasswordResetEmail("member@gmail.com", resetUrl, language);

        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo(subject);
        MailBodies bodies = mailBodies(message.getContent());
        // 유효시간은 UserService 정책 상수에서 온다.
        assertThat(validity).contains(String.valueOf(UserService.RESET_TOKEN_VALIDITY.toMinutes()));
        assertThat(bodies.html())
                .contains(validity, resetUrl, "Travel Diary")
                .doesNotContain("??").doesNotContain("{0}");
        assertThat(bodies.plainText()).contains(validity, resetUrl);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "KOREAN              | [Travel Diary] 계정 복구 안내          | 계정 복구      | 30분",
            "ENGLISH             | [Travel Diary] Recover your account  | Recover account | 30 minutes",
            "JAPANESE            | [Travel Diary] アカウント復旧のご案内    | アカウント復旧   | 30分",
            "CHINESE_SIMPLIFIED  | [Travel Diary] 账号恢复指引            | 恢复账号        | 30 分钟",
            "CHINESE_TRADITIONAL | [Travel Diary] 帳號復原指引            | 復原帳號        | 30 分鐘"
    })
    void accountRecoveryMailFollowsTheRequestedLanguage(SupportedLanguage language, String subject,
                                                        String button, String validity)
            throws Exception {
        String recoveryUrl =
                "https://travel.example/users/recover-account/confirm?token=safe-token";

        emailService.sendAccountRecoveryEmail("member@gmail.com", recoveryUrl, 30, language);

        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo(subject).doesNotContain("safe-token");
        MailBodies bodies = mailBodies(message.getContent());
        assertThat(validity).contains(
                String.valueOf(AccountRecoveryService.RECOVERY_TOKEN_VALIDITY.toMinutes()));
        assertThat(bodies.html())
                .contains(button, validity, recoveryUrl, "Travel Diary")
                .doesNotContain("??").doesNotContain("{0}");
        assertThat(bodies.plainText()).contains(button, validity, recoveryUrl);
    }

    /**
     * 링크 유효시간은 계정의 남은 유예기간에 따라 30분보다 짧아질 수 있다.
     * 안내 문구도 실제 만료와 같은 값을 써야 한다.
     */
    @Test
    void accountRecoveryMailAnnouncesTheActualLinkLifetimeNotAlwaysThirtyMinutes()
            throws Exception {
        emailService.sendAccountRecoveryEmail(
                "member@gmail.com",
                "https://travel.example/users/recover-account/confirm?token=safe-token",
                8,
                SupportedLanguage.KOREAN);

        message.saveChanges();
        MailBodies bodies = mailBodies(message.getContent());
        assertThat(bodies.html()).contains("8분").doesNotContain("30분");
        assertThat(bodies.plainText()).contains("복구", "요청하지 않았다면");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "KOREAN              | [Travel Diary] 아이디를 안내해 드려요     | 로그인하기",
            "ENGLISH             | [Travel Diary] Here is your username    | Log in",
            "JAPANESE            | [Travel Diary] IDのご案内                | ログインする",
            "CHINESE_SIMPLIFIED  | [Travel Diary] 为你找回账号               | 前往登录",
            "CHINESE_TRADITIONAL | [Travel Diary] 為你找回帳號               | 前往登入"
    })
    void usernameRecoveryMailFollowsTheRequestedLanguageButKeepsTheUsername(
            SupportedLanguage language, String subject, String loginButton) throws Exception {
        emailService.sendUsernameRecoveryEmail(
                "member@gmail.com", "travel-member",
                "https://travel.example/login",
                "https://travel.example/users/find-password", language);

        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo(subject).doesNotContain("travel-member");
        MailBodies bodies = mailBodies(message.getContent());
        // 아이디 값 자체는 언어와 무관하게 그대로다.
        assertThat(bodies.html())
                .contains(loginButton, "travel-member", "Travel Diary")
                .doesNotContain("??").doesNotContain("{0}");
        assertThat(bodies.plainText())
                .contains("travel-member")
                .contains("https://travel.example/login")
                .contains("https://travel.example/users/find-password");
    }

    /** 지원하지 않는 언어가 들어와도 사이트 기본값(한국어)으로 보낸다. */
    @Test
    void unknownLanguageFallsBackToKorean() throws Exception {
        emailService.sendPasswordResetEmail(
                "member@gmail.com", "https://travel.example/reset", null);

        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("[Travel Diary] 비밀번호를 재설정해 주세요");
    }

    @Test
    void springMailFailureIsConvertedToTheProjectDeliveryException() {
        doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(message);

        assertThatThrownBy(() -> emailService.sendVerificationEmail(
                "member@gmail.com", "safe-token", SupportedLanguage.KOREAN))
                .isInstanceOf(EmailDeliveryException.class)
                .hasCauseInstanceOf(MailSendException.class)
                .hasMessageNotContaining("safe-token");
    }

    @Test
    void verificationUrlUsesConfiguredBaseUrlRatherThanJavaLocalhost() {
        assertThat(emailService.buildVerificationUrl("safe-token"))
                .isEqualTo("https://travel.example/users/verify?token=safe-token");
    }

    @Test
    void blankMailConfigurationRejectsDeliveryBeforeJavaMailSenderIsUsed() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        EmailServiceImpl unconfiguredEmailService = new EmailServiceImpl(
                mailSender, templateEngine, messages, " ", "", "https://travel.example");

        assertThatThrownBy(() -> unconfiguredEmailService.sendVerificationEmail(
                "member@gmail.com", "safe-token", SupportedLanguage.KOREAN))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("MAIL_USERNAME / MAIL_PASSWORD")
                .hasMessageNotContaining("safe-token");

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private MailBodies mailBodies(Object content) throws Exception {
        StringBuilder plain = new StringBuilder();
        StringBuilder html = new StringBuilder();
        collectBodies(content, plain, html);
        return new MailBodies(plain.toString(), html.toString());
    }

    private void collectBodies(Object content, StringBuilder plain, StringBuilder html) throws Exception {
        if (content instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart part = multipart.getBodyPart(index);
                Object partContent = part.getContent();
                if (partContent instanceof Multipart) {
                    collectBodies(partContent, plain, html);
                } else if (part.isMimeType("text/plain")) {
                    plain.append(partContent);
                } else if (part.isMimeType("text/html")) {
                    html.append(partContent);
                } else {
                    collectBodies(partContent, plain, html);
                }
            }
        }
    }

    private record MailBodies(String plainText, String html) {
    }
}
