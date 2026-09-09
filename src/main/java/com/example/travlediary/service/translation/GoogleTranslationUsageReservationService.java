package com.example.travlediary.service.translation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleTranslationUsageReservationService implements TranslationUsageReservationGate {
    private final TranslationDailyUsageGate dailyUsage;
    private final TranslationMonthlyUsageGate monthlyUsage;

    @Transactional
    @Override
    public void reserve(String sourceText, String ipAddress, Long userId) {
        dailyUsage.reserve(sourceText, ipAddress, userId);
        monthlyUsage.reserve(sourceText);
    }
}
