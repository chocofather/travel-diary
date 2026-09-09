package com.example.travlediary.service.translation;

public record ContentTranslationResponse(
        String status,
        String translatedText,
        String sourceLanguage,
        String targetLanguage,
        boolean cached,
        long retryAfterSeconds
) {
    public static ContentTranslationResponse ready(
            String text, String sourceLanguage, String targetLanguage, boolean cached) {
        return new ContentTranslationResponse(
                "READY", text, sourceLanguage, targetLanguage, cached, 0L);
    }

    public static ContentTranslationResponse processing(
            String sourceLanguage, String targetLanguage, long retryAfterSeconds) {
        return new ContentTranslationResponse(
                "PROCESSING", null, sourceLanguage, targetLanguage, false,
                Math.max(1L, retryAfterSeconds));
    }
}
