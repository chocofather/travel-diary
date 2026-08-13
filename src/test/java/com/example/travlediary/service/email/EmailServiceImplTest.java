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
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

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

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        emailService = new EmailServiceImpl(
                mailSender,
                templateEngine,
                "mailer@gmail.com",
                "test-app-password",
                "https://travel.example");
    }

    @Test
    void verificationMailHasExplicitSenderRecipientSubjectAndTextAlternatives() throws Exception {
        emailService.sendVerificationEmail("member@gmail.com", "safe-token");

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
                "https://travel.example/users/find-password");

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

        emailService.sendPasswordResetEmail("member@gmail.com", resetUrl);

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

    @Test
    void springMailFailureIsConvertedToTheProjectDeliveryException() {
        doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(message);

        assertThatThrownBy(() -> emailService.sendVerificationEmail(
                "member@gmail.com", "safe-token"))
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
        EmailServiceImpl unconfiguredEmailService = new EmailServiceImpl(
                mailSender, templateEngine, " ", "", "https://travel.example");

        assertThatThrownBy(() -> unconfiguredEmailService.sendVerificationEmail(
                "member@gmail.com", "safe-token"))
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
