package com.example.travlediary.service.translation;

import java.util.Optional;

public interface TranslationSourceReader {
    TranslatableContentType contentType();

    Optional<TranslationSourceSnapshot> findVisible(Long contentId);

    Optional<TranslationSourceSnapshot> findVisibleForUpdate(Long contentId);

    default void correctSourceLanguage(TranslationSourceSnapshot snapshot, String detectedLanguage) {
    }
}
