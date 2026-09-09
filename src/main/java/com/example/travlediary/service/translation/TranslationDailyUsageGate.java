package com.example.travlediary.service.translation;

public interface TranslationDailyUsageGate {
    void reserve(String sourceText, String ipAddress, Long userId);
}
