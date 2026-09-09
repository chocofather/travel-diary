package com.example.travlediary.service.translation;

import java.sql.Timestamp;

public record TranslationSourceSnapshot(
        TranslatableContentType contentType,
        Long contentId,
        String sourceField,
        String text,
        String sourceLanguage,
        Timestamp updatedAt,
        String mimeType
) {
    public TranslationSourceSnapshot(
            TranslatableContentType contentType,
            Long contentId,
            String sourceField,
            String text,
            String sourceLanguage,
            Timestamp updatedAt) {
        this(contentType, contentId, sourceField, text, sourceLanguage, updatedAt, "text/plain");
    }
}
