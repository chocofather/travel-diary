package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.ContentTranslationCache;
import com.example.travlediary.model.translation.SharedTranslationCache;
import com.example.travlediary.repository.translation.ContentTranslationCacheMapper;
import com.example.travlediary.repository.translation.SharedTranslationCacheMapper;
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
    private final SharedTranslationCacheMapper sharedCacheMapper;
    private final MachineTranslationClient translationClient;
    private final TranslationRateLimiter rateLimiter;
    private final TranslationProperties properties;
    private final TranslationCacheFinalizer cacheFinalizer;
    private final TranslationUsageReservationGate usageReservation;
    private final TranslationProviderMetadata providerMetadata;
    private final Clock clock = Clock.systemUTC();

    public ContentTranslationResponse translate(TranslatableContentType type,
                                                Long contentId,
                                                String targetLanguage,
                                                String ipAddress,
                                                Long userId) {
        return translate(type, contentId, "content", targetLanguage, ipAddress, userId);
    }

    public ContentTranslationResponse translate(TranslatableContentType type,
                                                Long contentId,
                                                String sourceField,
                                                String targetLanguage,
                                                String ipAddress,
                                                Long userId) {
        rateLimiter.checkRequest(ipAddress, userId);
        TranslationSourceSnapshot source = sourceRegistry.get(type).findVisible(contentId, sourceField)
                .orElseThrow(TranslationNotFoundException::new);
        if (source.text() == null) throw new TranslationNotFoundException();
        if (type != TranslatableContentType.USER_POST
                && source.text().codePointCount(0, source.text().length())
                > properties.maxCommentCharacters()) {
            throw new TranslationTooLongException();
        }
        if (!TranslationVisibility.shouldOffer(source.sourceLanguage(), targetLanguage)) {
            return ContentTranslationResponse.ready(
                    source.text(), source.sourceLanguage(), targetLanguage, true);
        }

        byte[] sourceHash = sourceHash(source.text());
        ContentTranslationCache cached = findContentCache(source, targetLanguage);
        if (isReadyFor(cached, sourceHash)) {
            return contentCacheResponse(source, targetLanguage, cached);
        }

        Instant now = clock.instant();
        SharedTranslationCache shared = findSharedCache(source, targetLanguage, sourceHash);
        if (isSharedReady(shared)) {
            return reuseShared(source, targetLanguage, sourceHash, shared, now);
        }
        if (isSharedWaiting(shared, now)) {
            return processing(source, targetLanguage, retryAfterSeconds(shared, now));
        }

        String contentLeaseToken = UUID.randomUUID().toString();
        if (!tryAcquireContentLease(
                source, targetLanguage, sourceHash, contentLeaseToken, now)) {
            ContentTranslationCache current = findContentCache(source, targetLanguage);
            if (isReadyFor(current, sourceHash)) {
                return contentCacheResponse(source, targetLanguage, current);
            }
            shared = findSharedCache(source, targetLanguage, sourceHash);
            if (isSharedReady(shared)) {
                return reuseShared(source, targetLanguage, sourceHash, shared, clock.instant());
            }
            long retryAfter = shared == null
                    ? retryAfterSeconds(current, now) : retryAfterSeconds(shared, now);
            return processing(source, targetLanguage, retryAfter);
        }

        shared = findSharedCache(source, targetLanguage, sourceHash);
        if (isSharedReady(shared)) {
            return reuseShared(source, targetLanguage, sourceHash, shared, clock.instant());
        }
        if (isSharedWaiting(shared, now)) {
            return processing(source, targetLanguage, retryAfterSeconds(shared, now));
        }

        String sharedLeaseToken = UUID.randomUUID().toString();
        if (!tryAcquireSharedLease(
                source, targetLanguage, sourceHash, sharedLeaseToken, now)) {
            SharedTranslationCache current = findSharedCache(source, targetLanguage, sourceHash);
            if (isSharedReady(current)) {
                return reuseShared(source, targetLanguage, sourceHash, current, clock.instant());
            }
            return processing(source, targetLanguage, retryAfterSeconds(current, now));
        }

        try {
            rateLimiter.checkExternalCall(ipAddress, userId);
        } catch (TranslationRateLimitException e) {
            releaseSharedLease(source, targetLanguage, sourceHash, sharedLeaseToken);
            markContentFailed(source, targetLanguage, sourceHash, contentLeaseToken,
                    now.plusSeconds(e.retryAfterSeconds()), now);
            throw e;
        }

        try {
            translationClient.prepare();
        } catch (RuntimeException e) {
            markBothFailed(source, targetLanguage, sourceHash, contentLeaseToken,
                    sharedLeaseToken, now.plus(properties.failureRetryAfter()), now);
            throw providerException(e);
        }

        try {
            usageReservation.reserve(source.text(), ipAddress, userId);
        } catch (TranslationDailyLimitException | TranslationMonthlyLimitException e) {
            releaseSharedLease(source, targetLanguage, sourceHash, sharedLeaseToken);
            markContentFailed(source, targetLanguage, sourceHash, contentLeaseToken,
                    now.plus(properties.failureRetryAfter()), now);
            throw e;
        } catch (RuntimeException e) {
            releaseSharedLease(source, targetLanguage, sourceHash, sharedLeaseToken);
            markContentFailed(source, targetLanguage, sourceHash, contentLeaseToken,
                    now.plus(properties.failureRetryAfter()), now);
            throw new MachineTranslationException("번역 사용량 확인에 실패했습니다.", e);
        }

        MachineTranslation translated;
        try {
            translated = translationClient.translate(
                    source.text(), source.sourceLanguage(), targetLanguage, source.mimeType());
        } catch (RuntimeException e) {
            Instant failedAt = clock.instant();
            markBothFailed(source, targetLanguage, sourceHash, contentLeaseToken,
                    sharedLeaseToken, failedAt.plus(properties.failureRetryAfter()), failedAt);
            throw providerException(e);
        }

        Timestamp completedAt = Timestamp.from(clock.instant());
        int sharedReady = sharedCacheMapper.markReady(
                sourceHash, source.sourceLanguage(), targetLanguage,
                providerMetadata.provider(), providerMetadata.profileFor(source.mimeType()), sharedLeaseToken,
                translated.translatedText(), translated.detectedSourceLanguage(), completedAt);
        if (sharedReady != 1) {
            SharedTranslationCache current = findSharedCache(source, targetLanguage, sourceHash);
            if (isSharedReady(current)) {
                return reuseShared(source, targetLanguage, sourceHash, current, clock.instant());
            }
            markContentFailed(source, targetLanguage, sourceHash, contentLeaseToken,
                    clock.instant(), clock.instant());
            throw new TranslationStaleException();
        }

        TranslationFinalization finalization = cacheFinalizer.markReady(
                type, contentId, source.sourceField(), targetLanguage, sourceHash,
                source.sourceLanguage(), contentLeaseToken, translated, completedAt);
        if (finalization.status() != TranslationFinalization.Status.READY) {
            markContentFailed(source, targetLanguage, sourceHash, contentLeaseToken,
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

    private ContentTranslationCache findContentCache(TranslationSourceSnapshot source,
                                                     String targetLanguage) {
        return cacheMapper.find(source.contentType().name(), source.contentId(),
                source.sourceField(), targetLanguage);
    }

    private SharedTranslationCache findSharedCache(TranslationSourceSnapshot source,
                                                   String targetLanguage,
                                                   byte[] sourceHash) {
        return sharedCacheMapper.find(
                sourceHash, source.sourceLanguage(), targetLanguage,
                providerMetadata.provider(), providerMetadata.profileFor(source.mimeType()));
    }

    private ContentTranslationResponse contentCacheResponse(
            TranslationSourceSnapshot source,
            String targetLanguage,
            ContentTranslationCache cache) {
        return ContentTranslationResponse.ready(
                cache.getTranslatedText(), effectiveSourceLanguage(source, cache),
                targetLanguage, true);
    }

    private ContentTranslationResponse reuseShared(
            TranslationSourceSnapshot source,
            String targetLanguage,
            byte[] sourceHash,
            SharedTranslationCache shared,
            Instant now) {
        MachineTranslation translation = new MachineTranslation(
                shared.getTranslatedText(), shared.getDetectedSourceLanguage());
        TranslationFinalization finalization = cacheFinalizer.upsertReady(
                source.contentType(), source.contentId(), source.sourceField(), targetLanguage,
                sourceHash, source.sourceLanguage(), shared.getProvider(), translation,
                Timestamp.from(now));
        if (finalization.status() == TranslationFinalization.Status.NOT_FOUND) {
            throw new TranslationNotFoundException();
        }
        if (finalization.status() != TranslationFinalization.Status.READY) {
            throw new TranslationStaleException();
        }
        return ContentTranslationResponse.ready(
                shared.getTranslatedText(), providerSourceLanguage(finalization.source(), translation),
                targetLanguage, true);
    }

    private ContentTranslationResponse processing(TranslationSourceSnapshot source,
                                                  String targetLanguage,
                                                  long retryAfterSeconds) {
        return ContentTranslationResponse.processing(
                source.sourceLanguage(), targetLanguage, retryAfterSeconds);
    }

    private boolean tryAcquireContentLease(TranslationSourceSnapshot source,
                                           String targetLanguage,
                                           byte[] sourceHash,
                                           String leaseToken,
                                           Instant now) {
        ContentTranslationCache cache = new ContentTranslationCache();
        cache.setContentType(source.contentType().name());
        cache.setContentId(source.contentId());
        cache.setSourceField(source.sourceField());
        cache.setTargetLanguage(targetLanguage);
        cache.setSourceHash(sourceHash);
        cache.setProvider(providerMetadata.provider());
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

    private boolean tryAcquireSharedLease(TranslationSourceSnapshot source,
                                          String targetLanguage,
                                          byte[] sourceHash,
                                          String leaseToken,
                                          Instant now) {
        SharedTranslationCache cache = new SharedTranslationCache();
        cache.setSourceHash(sourceHash);
        cache.setSourceLanguage(source.sourceLanguage());
        cache.setTargetLanguage(targetLanguage);
        cache.setProvider(providerMetadata.provider());
        cache.setTranslationProfile(providerMetadata.profileFor(source.mimeType()));
        cache.setLeaseToken(leaseToken);
        cache.setLeaseExpiresAt(Timestamp.from(now.plus(properties.leaseDuration())));
        cache.setCreatedAt(Timestamp.from(now));
        cache.setUpdatedAt(Timestamp.from(now));
        if (sharedCacheMapper.insertProcessing(cache) == 1) return true;
        return sharedCacheMapper.tryClaim(
                sourceHash, source.sourceLanguage(), targetLanguage,
                providerMetadata.provider(), providerMetadata.profileFor(source.mimeType()), leaseToken,
                Timestamp.from(now), Timestamp.from(now.plus(properties.leaseDuration()))) == 1;
    }

    private void markBothFailed(TranslationSourceSnapshot source,
                                String targetLanguage,
                                byte[] sourceHash,
                                String contentLeaseToken,
                                String sharedLeaseToken,
                                Instant retryAfter,
                                Instant now) {
        sharedCacheMapper.markFailed(
                sourceHash, source.sourceLanguage(), targetLanguage,
                providerMetadata.provider(), providerMetadata.profileFor(source.mimeType()), sharedLeaseToken,
                Timestamp.from(retryAfter), Timestamp.from(now));
        markContentFailed(source, targetLanguage, sourceHash, contentLeaseToken, retryAfter, now);
    }

    private void markContentFailed(TranslationSourceSnapshot source,
                                   String targetLanguage,
                                   byte[] sourceHash,
                                   String leaseToken,
                                   Instant retryAfter,
                                   Instant now) {
        cacheMapper.markFailed(
                source.contentType().name(), source.contentId(), source.sourceField(), targetLanguage,
                sourceHash, leaseToken, Timestamp.from(retryAfter), Timestamp.from(now));
    }

    private void releaseSharedLease(TranslationSourceSnapshot source,
                                    String targetLanguage,
                                    byte[] sourceHash,
                                    String sharedLeaseToken) {
        sharedCacheMapper.deleteProcessing(
                sourceHash, source.sourceLanguage(), targetLanguage,
                providerMetadata.provider(), providerMetadata.profileFor(source.mimeType()), sharedLeaseToken);
    }

    private boolean isReadyFor(ContentTranslationCache cache, byte[] sourceHash) {
        return cache != null
                && "READY".equals(cache.getStatus())
                && cache.getTranslatedText() != null
                && Arrays.equals(cache.getSourceHash(), sourceHash);
    }

    private boolean isSharedReady(SharedTranslationCache cache) {
        return cache != null
                && "READY".equals(cache.getStatus())
                && cache.getTranslatedText() != null;
    }

    private boolean isSharedWaiting(SharedTranslationCache cache, Instant now) {
        if (cache == null) return false;
        if ("PROCESSING".equals(cache.getStatus())) {
            return cache.getLeaseExpiresAt() != null
                    && cache.getLeaseExpiresAt().toInstant().isAfter(now);
        }
        if ("FAILED".equals(cache.getStatus())) {
            return cache.getRetryAfter() != null
                    && cache.getRetryAfter().toInstant().isAfter(now);
        }
        return false;
    }

    private long retryAfterSeconds(ContentTranslationCache cache, Instant now) {
        Timestamp retryAt = cache == null ? null
                : "FAILED".equals(cache.getStatus()) ? cache.getRetryAfter() : cache.getLeaseExpiresAt();
        return retryAfterSeconds(retryAt, now);
    }

    private long retryAfterSeconds(SharedTranslationCache cache, Instant now) {
        Timestamp retryAt = cache == null ? null
                : "FAILED".equals(cache.getStatus()) ? cache.getRetryAfter() : cache.getLeaseExpiresAt();
        return retryAfterSeconds(retryAt, now);
    }

    private long retryAfterSeconds(Timestamp retryAt, Instant now) {
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
