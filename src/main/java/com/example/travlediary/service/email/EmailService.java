package com.example.travlediary.service.email;

public interface EmailService {
    void sendVerificationEmail(String to, String token);

    void sendUsernameRecoveryEmail(String to, String username,
                                   String loginUrl, String passwordResetUrl);

    void sendPasswordResetEmail(String to, String resetUrl);

    void sendEmail(String to, String subject, String htmlContent);

}
