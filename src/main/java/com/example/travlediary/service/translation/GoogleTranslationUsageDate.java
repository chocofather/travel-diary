package com.example.travlediary.service.translation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

final class GoogleTranslationUsageDate {
    private GoogleTranslationUsageDate() {
    }

    static LocalDate from(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
