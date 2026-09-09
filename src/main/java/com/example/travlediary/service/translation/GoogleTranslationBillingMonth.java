package com.example.travlediary.service.translation;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

final class GoogleTranslationBillingMonth {
    static final ZoneId PACIFIC_TIME = ZoneId.of("America/Los_Angeles");

    private GoogleTranslationBillingMonth() {
    }

    static String monthKey(Instant instant) {
        return YearMonth.from(instant.atZone(PACIFIC_TIME)).toString();
    }
}
