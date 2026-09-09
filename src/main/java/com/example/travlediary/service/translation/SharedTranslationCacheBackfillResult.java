package com.example.travlediary.service.translation;

public record SharedTranslationCacheBackfillResult(
        long scanned,
        long inserted,
        long skipped
) {
}
