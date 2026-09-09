package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.ContentTranslationCache;
import com.example.travlediary.repository.translation.ContentTranslationCacheMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class TranslationCacheFinalizer {
    private final TranslationSourceRegistry sourceRegistry;
    private final ContentTranslationCacheMapper cacheMapper;

    @Transactional
    public TranslationFinalization markReady(
            TranslatableContentType type,
            Long contentId,
            String sourceField,
            String targetLanguage,
            byte[] expectedSourceHash,
            String expectedSourceLanguage,
            String leaseToken,
            MachineTranslation translation,
            Timestamp completedAt) {
        TranslationSourceReader reader = sourceRegistry.get(type);
        TranslationSourceSnapshot current = reader.findVisibleForUpdate(contentId).orElse(null);
        if (current == null) return TranslationFinalization.notFound();
        if (!matches(current, sourceField, expectedSourceHash, expectedSourceLanguage)) {
            return TranslationFinalization.stale(current);
        }
        int updated = cacheMapper.markReady(
                type.name(), contentId, sourceField, targetLanguage, expectedSourceHash,
                leaseToken, translation.translatedText(), translation.detectedSourceLanguage(), completedAt);
        if (updated != 1) return TranslationFinalization.stale(current);
        reader.correctSourceLanguage(current, translation.detectedSourceLanguage());
        return TranslationFinalization.ready(current);
    }

    @Transactional
    public TranslationFinalization upsertReady(
            TranslatableContentType type,
            Long contentId,
            String sourceField,
            String targetLanguage,
            byte[] expectedSourceHash,
            String expectedSourceLanguage,
            String provider,
            MachineTranslation translation,
            Timestamp completedAt) {
        TranslationSourceReader reader = sourceRegistry.get(type);
        TranslationSourceSnapshot current = reader.findVisibleForUpdate(contentId).orElse(null);
        if (current == null) return TranslationFinalization.notFound();
        if (!matches(current, sourceField, expectedSourceHash, expectedSourceLanguage)) {
            return TranslationFinalization.stale(current);
        }

        ContentTranslationCache cache = new ContentTranslationCache();
        cache.setContentType(type.name());
        cache.setContentId(contentId);
        cache.setSourceField(sourceField);
        cache.setTargetLanguage(targetLanguage);
        cache.setSourceHash(expectedSourceHash);
        cache.setDetectedSourceLanguage(translation.detectedSourceLanguage());
        cache.setTranslatedText(translation.translatedText());
        cache.setProvider(provider);
        cache.setStatus("READY");
        cache.setCreatedAt(completedAt);
        cache.setUpdatedAt(completedAt);
        if (cacheMapper.upsertReady(cache) < 1) return TranslationFinalization.stale(current);
        reader.correctSourceLanguage(current, translation.detectedSourceLanguage());
        return TranslationFinalization.ready(current);
    }

    private boolean matches(TranslationSourceSnapshot current,
                            String sourceField,
                            byte[] expectedSourceHash,
                            String expectedSourceLanguage) {
        return sourceField.equals(current.sourceField())
                && expectedSourceLanguage.equals(current.sourceLanguage())
                && current.text() != null
                && Arrays.equals(expectedSourceHash, ContentTranslationService.sourceHash(current.text()));
    }
}
