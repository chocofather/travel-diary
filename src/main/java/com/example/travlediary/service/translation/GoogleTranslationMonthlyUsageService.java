package com.example.travlediary.service.translation;

import com.example.travlediary.repository.translation.GoogleTranslationMonthlyUsageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

@Service
public class GoogleTranslationMonthlyUsageService implements TranslationMonthlyUsageGate {
    private final GoogleTranslationMonthlyUsageMapper usageMapper;
    private final TranslationProperties properties;
    private final Clock clock;

    @Autowired
    public GoogleTranslationMonthlyUsageService(GoogleTranslationMonthlyUsageMapper usageMapper,
                                                TranslationProperties properties) {
        this(usageMapper, properties, Clock.systemUTC());
    }

    GoogleTranslationMonthlyUsageService(GoogleTranslationMonthlyUsageMapper usageMapper,
                                         TranslationProperties properties,
                                         Clock clock) {
        this.usageMapper = usageMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void reserve(String sourceText) {
        if (!properties.monthlyCharacterLimitEnabled()) return;

        long characters = sourceText.codePointCount(0, sourceText.length());
        Instant currentInstant = clock.instant();
        Timestamp now = Timestamp.from(currentInstant);
        String monthKey = GoogleTranslationBillingMonth.monthKey(currentInstant);
        usageMapper.insertMonthIfAbsent(monthKey, now);
        if (usageMapper.reserveIfWithinLimit(
                monthKey, characters, properties.monthlyCharacterLimit(), now) != 1) {
            throw new TranslationMonthlyLimitException();
        }
    }
}
