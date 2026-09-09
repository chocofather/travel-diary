package com.example.travlediary.service.translation;

import java.util.Optional;

public interface TranslationSourceReader {
    TranslatableContentType contentType();

    Optional<TranslationSourceSnapshot> findVisible(Long contentId);

    default Optional<TranslationSourceSnapshot> findVisible(Long contentId, String sourceField) {
        return findVisible(contentId)
                .filter(source -> source.sourceField().equals(sourceField));
    }

    Optional<TranslationSourceSnapshot> findVisibleForUpdate(Long contentId);

    default Optional<TranslationSourceSnapshot> findVisibleForUpdate(
            Long contentId, String sourceField) {
        return findVisibleForUpdate(contentId)
                .filter(source -> source.sourceField().equals(sourceField));
    }

    default void correctSourceLanguage(TranslationSourceSnapshot snapshot, String detectedLanguage) {
    }
}
