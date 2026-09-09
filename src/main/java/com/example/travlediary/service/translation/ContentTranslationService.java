package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.ContentTranslationCache;
import com.example.travlediary.repository.translation.ContentTranslationCacheMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentTranslationService {
    private final TranslationSourceRegistry sourceRegistry;
    private final ContentTranslationCacheMapper cacheMapper;
    private final MachineTranslationClient translationClient;
    private final TranslationRateLimiter rateLimiter;
    private final TranslationProperties properties;
    private final TranslationCacheFinalizer cacheFinalizer;
    private final TranslationUsageReservationGate usageReservation;
    private final Clock clock = Clock.systemUTC();

    public ContentTranslationResponse translate(TranslatableContentType type,
                                                Long contentId,
                                                String targetLanguage,
                                                String ipAddress,
                                                Long userId) {
        rateLimiter.checkRequest(ipAddress, userId);
        TranslationSourceReader reader = sourceRegistry.get(type);
        TranslationSourceSnapshot source = reader.findVisible(contentId)
                .orElseThrow(TranslationNotFoundException::new);
        if (source.text() == null) throw new TranslationNotFoundException();
        if (source.text().codePointCount(0, source.text().length()) > properties.maxCommentCharacters()) {
            throw new TranslationTooLongException();
        }
        if (!TranslationVisibility.shouldOffer(source.sourceLanguage(), targetLanguage)) {
            return ContentTranslationResponse.ready(
                    source.text(), source.sourceLanguage(), targetLanguage, true);
        }

        byte[] sourceHash = sourceHash(source.text());
        ContentTranslationCache cached = cacheMapper.find(
                type.name(), contentId, source.sourceField(), targetLanguage);
        if (isReadyFor(cached, sourceHash)) {
            return ContentTranslationResponse.ready(
                    cached.getTranslatedText(), effectiveSourceLanguage(source, cached),
                    targetLanguage, true);
        }

        Instant now = clock.instant();
        String leaseToken = UUID.randomUUID().toString();
        boolean acquired = tryAcquireLease(
                source, targetLanguage, sourceHash, leaseToken, now);
        if (!acquired) {
            ContentTranslationCache current = cacheMapper.find(
                    type.name(), contentId, source.sourceField(), targetLanguage);
            if (isReadyFor(current, sourceHash)) {
                return ContentTranslationResponse.ready(
                        current.getTranslatedText(), effectiveSourceLanguage(source, current),
                        targetLanguage, true);
            }
            return ContentTranslationResponse.processing(
                    source.sourceLanguage(), targetLanguage, retryAfterSeconds(current, now));
        }

        try {
            rateLimiter.checkExternalCall(ipAddress, userId);
        } catch (TranslationRateLimitException e) {
            markFailed(source, targetLanguage, sourceHash, leaseToken,
                    now.plusSeconds(e.retryAfterSeconds()), now);
            throw e;
        }

        try {
            translationClient.prepare();
        } catch (RuntimeException e) {
            markFailed(source, targetLanguage, sourceHash, leaseToken,
                    now.plus(properties.failureRetryAfter()), now);
            throw providerException(e);
        }

        try {
            usageReservation.reserve(source.text(), ipAddress, userId);
        } catch (TranslationDailyLimitException | TranslationMonthlyLimitException e) {
            markFailed(source, targetLanguage, sourceHash, leaseToken,
                    now.plus(properties.failureRetryAfter()), now);
            throw e;
        } catch (RuntimeException e) {
            markFailed(source, targetLanguage, sourceHash, leaseToken,
                    now.plus(properties.failureRetryAfter()), now);
            throw new MachineTranslationException("번역 사용량 확인에 실패했습니다.", e);
        }

        MachineTranslation translated;
        try {
            translated = translationClient.translate(
                    source.text(), source.sourceLanguage(), targetLanguage);
        } catch (RuntimeException e) {
            markFailed(source, targetLanguage, sourceHash, leaseToken,
                    now.plus(properties.failureRetryAfter()), clock.instant());
            throw providerException(e);
        }

        Timestamp completedAt = Timestamp.from(clock.instant());
        TranslationFinalization finalization = cacheFinalizer.markReady(
                type, contentId, source.sourceField(), targetLanguage, sourceHash,
                leaseToken, translated, completedAt);
        if (finalization.status() != TranslationFinalization.Status.READY) {
            markFailed(source, targetLanguage, sourceHash, leaseToken,
                    clock.instant(), clock.instant());
            if (finalization.status() == TranslationFinalization.Status.NOT_FOUND) {
                throw new TranslationNotFoundException();
            }
            throw new TranslationStaleException();
        }
        TranslationSourceSnapshot currentSource = finalization.source();
        return ContentTranslationResponse.ready(
                translated.translatedText(), providerSourceLanguage(currentSource, translated),
                targetLanguage, false);
    }

    private boolean tryAcquireLease(TranslationSourceSnapshot source, String targetLanguage,
                                    byte[] sourceHash, String leaseToken, Instant now) {
        ContentTranslationCache cache = new ContentTranslationCache();
        cache.setContentType(source.contentType().name());
        cache.setContentId(source.contentId());
        cache.setSourceField(source.sourceField());
        cache.setTargetLanguage(targetLanguage);
        cache.setSourceHash(sourceHash);
        cache.setProvider("GOOGLE");
        cache.setLeaseToken(leaseToken);
        cache.setLeaseExpiresAt(Timestamp.from(now.plus(properties.leaseDuration())));
        cache.setCreatedAt(Timestamp.from(now));
        cache.setUpdatedAt(Timestamp.from(now));
        if (cacheMapper.insertProcessing(cache) == 1) return true;
        return cacheMapper.tryClaim(
                source.contentType().name(), source.contentId(), source.sourceField(), targetLanguage,
                sourceHash, leaseToken, Timestamp.from(now),
                Timestamp.from(now.plus(properties.leaseDuration()))) == 1;
    }

    private void markFailed(TranslationSourceSnapshot source, String targetLanguage,
                            byte[] sourceHash, String leaseToken, Instant retryAfter, Instant now) {
        cacheMapper.markFailed(
                source.contentType().name(), source.contentId(), source.sourceField(), targetLanguage,
                sourceHash, leaseToken, Timestamp.from(retryAfter), Timestamp.from(now));
    }

    private boolean isReadyFor(ContentTranslationCache cache, byte[] sourceHash) {
        return cache != null
                && "READY".equals(cache.getStatus())
                && cache.getTranslatedText() != null
                && Arrays.equals(cache.getSourceHash(), sourceHash);
    }

    private long retryAfterSeconds(ContentTranslationCache cache, Instant now) {
        Timestamp retryAt = cache == null ? null
                : "FAILED".equals(cache.getStatus()) ? cache.getRetryAfter() : cache.getLeaseExpiresAt();
        if (retryAt == null) return 1L;
        long millis = Duration.between(now, retryAt.toInstant()).toMillis();
        return Math.max(1L, (millis + 999L) / 1000L);
    }

    private String effectiveSourceLanguage(TranslationSourceSnapshot source,
                                           ContentTranslationCache cache) {
        return cache.getDetectedSourceLanguage() == null
                ? source.sourceLanguage() : cache.getDetectedSourceLanguage();
    }

    private String providerSourceLanguage(TranslationSourceSnapshot source,
                                          MachineTranslation translated) {
        return translated.detectedSourceLanguage() == null
                ? source.sourceLanguage() : translated.detectedSourceLanguage();
    }

    private MachineTranslationException providerException(RuntimeException exception) {
        if (exception instanceof MachineTranslationException machineTranslationException) {
            return machineTranslationException;
        }
        return new MachineTranslationException("번역 provider 요청에 실패했습니다.", exception);
    }

    static byte[] sourceHash(String sourceText) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(sourceText.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
