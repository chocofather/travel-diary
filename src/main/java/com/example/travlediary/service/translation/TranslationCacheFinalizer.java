package com.example.travlediary.service.translation;

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
            String leaseToken,
            MachineTranslation translation,
            Timestamp completedAt) {
        TranslationSourceReader reader = sourceRegistry.get(type);
        TranslationSourceSnapshot current = reader.findVisibleForUpdate(contentId).orElse(null);
        if (current == null) return TranslationFinalization.notFound();
        if (!sourceField.equals(current.sourceField())
                || !Arrays.equals(expectedSourceHash, ContentTranslationService.sourceHash(current.text()))) {
            return TranslationFinalization.stale(current);
        }
        int updated = cacheMapper.markReady(
                type.name(), contentId, sourceField, targetLanguage, expectedSourceHash,
                leaseToken, translation.translatedText(), translation.detectedSourceLanguage(), completedAt);
        if (updated != 1) return TranslationFinalization.stale(current);
        reader.correctSourceLanguage(current, translation.detectedSourceLanguage());
        return TranslationFinalization.ready(current);
    }
}
