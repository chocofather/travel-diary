package com.example.travlediary.service.email;

public interface EmailService {
    // ① 인증 링크 전송
    void sendVerificationEmail(String to, String subject, String token);

    // ② 일반 메일 전송
/*
    void sendEmail(String to, String subject, String content);
*/
    void sendEmail(String to, String subject, String htmlContent);   // ← 일반 메일

}
