package com.example.travlediary.service.translation;

public record UserPostTranslationResponse(
        String status,
        String translatedTitle,
        String translatedContent,
        boolean cached,
        long retryAfterSeconds
) {
    public static UserPostTranslationResponse ready(
            String translatedTitle, String translatedContent, boolean cached) {
        return new UserPostTranslationResponse(
                "READY", translatedTitle, translatedContent, cached, 0L);
    }

    public static UserPostTranslationResponse processing(
            String translatedTitle, String translatedContent, long retryAfterSeconds) {
        return new UserPostTranslationResponse(
                "PROCESSING", translatedTitle, translatedContent, false,
                Math.max(1L, retryAfterSeconds));
    }
}
