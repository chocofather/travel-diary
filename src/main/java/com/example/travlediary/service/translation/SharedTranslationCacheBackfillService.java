package com.example.travlediary.service.translation;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.translation.ContentTranslationCache;
import com.example.travlediary.model.translation.SharedTranslationCache;
import com.example.travlediary.repository.translation.ContentTranslationCacheMapper;
import com.example.travlediary.repository.translation.SharedTranslationCacheMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class SharedTranslationCacheBackfillService {
    private static final int MAX_BATCH_SIZE = 2_000;

    private final ContentTranslationCacheMapper contentCacheMapper;
    private final SharedTranslationCacheMapper sharedCacheMapper;
    private final TranslationSourceRegistry sourceRegistry;
    private final TranslationProviderMetadata providerMetadata;
    private final int batchSize;

    public SharedTranslationCacheBackfillService(
            ContentTranslationCacheMapper contentCacheMapper,
            SharedTranslationCacheMapper sharedCacheMapper,
            TranslationSourceRegistry sourceRegistry,
            TranslationProviderMetadata providerMetadata,
            @Value("${translation.backfill.shared-cache.batch-size:500}") int batchSize) {
        this.contentCacheMapper = contentCacheMapper;
        this.sharedCacheMapper = sharedCacheMapper;
        this.sourceRegistry = sourceRegistry;
        this.providerMetadata = providerMetadata;
        this.batchSize = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
    }

    public SharedTranslationCacheBackfillResult run() {
        long cursor = 0L;
        long scanned = 0L;
        long inserted = 0L;
        long skipped = 0L;

        while (true) {
            List<ContentTranslationCache> rows =
                    contentCacheMapper.findReadyAfter(cursor, batchSize);
            if (rows == null || rows.isEmpty()) break;

            for (ContentTranslationCache row : rows) {
                cursor = Math.max(cursor, row.getId());
                scanned++;
                Optional<SharedTranslationCache> candidate = toSharedCache(row);
                if (candidate.isEmpty()) {
                    skipped++;
                    continue;
                }
                if (sharedCacheMapper.insertReadyIfAbsent(candidate.get()) == 1) {
                    inserted++;
                } else {
                    skipped++;
                }
            }
        }
        return new SharedTranslationCacheBackfillResult(scanned, inserted, skipped);
    }

    private Optional<SharedTranslationCache> toSharedCache(ContentTranslationCache cached) {
        if (cached == null
                || cached.getId() == null
                || !"READY".equals(cached.getStatus())
                || cached.getTranslatedText() == null
                || cached.getSourceHash() == null
                || cached.getSourceHash().length != 32
                || !providerMetadata.provider().equals(cached.getProvider())) {
            return Optional.empty();
        }

        TranslatableContentType type;
        TranslationSourceReader reader;
        try {
            type = TranslatableContentType.valueOf(cached.getContentType());
            reader = sourceRegistry.get(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }

        TranslationSourceSnapshot source = reader.findVisible(
                cached.getContentId(), cached.getSourceField()).orElse(null);
        if (source == null
                || source.text() == null
                || !Objects.equals(cached.getSourceField(), source.sourceField())
                || SupportedLanguage.fromLanguageTag(source.sourceLanguage()).isEmpty()
                || SupportedLanguage.fromLanguageTag(cached.getTargetLanguage()).isEmpty()
                || !Arrays.equals(cached.getSourceHash(),
                ContentTranslationService.sourceHash(source.text()))) {
            return Optional.empty();
        }

        Timestamp now = Timestamp.from(Instant.now());
        SharedTranslationCache shared = new SharedTranslationCache();
        shared.setSourceHash(cached.getSourceHash());
        shared.setSourceLanguage(source.sourceLanguage());
        shared.setTargetLanguage(cached.getTargetLanguage());
        shared.setProvider(cached.getProvider());
        shared.setTranslationProfile(providerMetadata.profileFor(source.mimeType()));
        shared.setDetectedSourceLanguage(cached.getDetectedSourceLanguage());
        shared.setTranslatedText(cached.getTranslatedText());
        shared.setStatus("READY");
        shared.setCreatedAt(now);
        shared.setUpdatedAt(now);
        return Optional.of(shared);
    }
}
