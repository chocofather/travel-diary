package com.example.travlediary.service.translation;

public record TitleContentTranslationResponse(
        String status,
        String translatedTitle,
        String translatedContent,
        boolean cached,
        long retryAfterSeconds
) {
    public static TitleContentTranslationResponse ready(
            String translatedTitle, String translatedContent, boolean cached) {
        return new TitleContentTranslationResponse(
                "READY", translatedTitle, translatedContent, cached, 0L);
    }

    public static TitleContentTranslationResponse processing(
            String translatedTitle, String translatedContent, long retryAfterSeconds) {
        return new TitleContentTranslationResponse(
                "PROCESSING", translatedTitle, translatedContent, false,
                Math.max(1L, retryAfterSeconds));
    }
}
