package com.example.travlediary.service.translation;

import com.example.travlediary.repository.translation.GoogleTranslationDailyUsageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class GoogleTranslationDailyUsageService implements TranslationDailyUsageGate {
    private final GoogleTranslationDailyUsageMapper usageMapper;
    private final TranslationProperties properties;
    private final TranslationIpSubjectHasher ipSubjectHasher;
    private final Clock clock;

    @Autowired
    public GoogleTranslationDailyUsageService(GoogleTranslationDailyUsageMapper usageMapper,
                                              TranslationProperties properties,
                                              TranslationIpSubjectHasher ipSubjectHasher) {
        this(usageMapper, properties, ipSubjectHasher, Clock.systemUTC());
    }

    GoogleTranslationDailyUsageService(GoogleTranslationDailyUsageMapper usageMapper,
                                       TranslationProperties properties,
                                       TranslationIpSubjectHasher ipSubjectHasher,
                                       Clock clock) {
        this.usageMapper = usageMapper;
        this.properties = properties;
        this.ipSubjectHasher = ipSubjectHasher;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void reserve(String sourceText, String ipAddress, Long userId) {
        long characters = sourceText.codePointCount(0, sourceText.length());
        Instant currentInstant = clock.instant();
        LocalDate usageDate = GoogleTranslationUsageDate.from(currentInstant);
        Timestamp now = Timestamp.from(currentInstant);

        if (userId != null) {
            reserveUser(usageDate, userId, characters, now);
            return;
        }
        reserveIp(usageDate, ipSubjectHasher.hash(ipAddress), characters, now);
    }

    private void reserveUser(LocalDate usageDate, Long userId, long characters, Timestamp now) {
        usageMapper.insertUserDayIfAbsent(usageDate, userId, now);
        if (usageMapper.reserveUserIfWithinLimit(
                usageDate, userId, characters,
                properties.authenticatedDailyCharacterLimit(), now) != 1) {
            throw new TranslationDailyLimitException();
        }
    }

    private void reserveIp(LocalDate usageDate, byte[] ipHash, long characters, Timestamp now) {
        usageMapper.insertIpDayIfAbsent(usageDate, ipHash, now);
        if (usageMapper.reserveIpIfWithinLimit(
                usageDate, ipHash, characters,
                properties.anonymousDailyCharacterLimit(), now) != 1) {
            throw new TranslationDailyLimitException();
        }
    }
}
