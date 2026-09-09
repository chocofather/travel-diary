package com.example.travlediary.service.translation;

public interface TranslationRateLimiter {
    void checkRequest(String ipAddress, Long userId);

    void checkExternalCall(String ipAddress, Long userId);
}
