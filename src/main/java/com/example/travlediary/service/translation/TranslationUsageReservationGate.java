package com.example.travlediary.service.translation;

public interface TranslationUsageReservationGate {
    void reserve(String sourceText, String ipAddress, Long userId);
}
