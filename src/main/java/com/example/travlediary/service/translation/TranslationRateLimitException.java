package com.example.travlediary.service.translation;

public class TranslationRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public TranslationRateLimitException(long retryAfterSeconds) {
        super("번역 요청이 너무 많습니다.");
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
