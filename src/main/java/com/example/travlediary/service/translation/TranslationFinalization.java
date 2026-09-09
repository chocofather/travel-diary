package com.example.travlediary.service.translation;

public record TranslationFinalization(Status status, TranslationSourceSnapshot source) {
    public enum Status { READY, NOT_FOUND, STALE }

    public static TranslationFinalization ready(TranslationSourceSnapshot source) {
        return new TranslationFinalization(Status.READY, source);
    }

    public static TranslationFinalization notFound() {
        return new TranslationFinalization(Status.NOT_FOUND, null);
    }

    public static TranslationFinalization stale(TranslationSourceSnapshot source) {
        return new TranslationFinalization(Status.STALE, source);
    }
}
