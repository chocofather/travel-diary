package com.example.travlediary.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    /* 1️⃣ 필드 선언 */
    private final JavaMailSender mailSender;

    @Value("${custom.server-url}")
    private String serverUrl;

    /* 2️⃣ 생성자 주입 */
    @Autowired
    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    /* (회원가입용) 인증 메일 */
    /* 이메일 인증 링크 발송 */
    @Override
    public void sendVerificationEmail(String to, String subject, String token) {
        String link = serverUrl + "/users/verify?token=" + token;
        String html = """
            <h3>이메일 인증</h3>
            <p>아래 링크를 클릭하여 이메일 인증을 완료하세요.</p>
            <p><a href="%s">이메일 인증하기</a></p>
            """.formatted(link);

        sendEmail(to, subject, html);   // ⬅️ 아래의 sendEmail 재사용
    }

    /* (공통) HTML 이메일 전송 */
    @Override
    public void sendEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // true : multipart,  UTF-8 인코딩
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);       // second param true → HTML

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("이메일 전송 실패", e);
        }
    }
}
