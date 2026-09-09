package com.example.travlediary.service.translation;

import java.sql.Timestamp;

public record TranslationSourceSnapshot(
        TranslatableContentType contentType,
        Long contentId,
        String sourceField,
        String text,
        String sourceLanguage,
        Timestamp updatedAt
) {
}
