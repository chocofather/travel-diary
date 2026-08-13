package com.example.travlediary.service.email;

public interface EmailService {
    void sendVerificationEmail(String to, String token);

    void sendEmail(String to, String subject, String htmlContent);

}
