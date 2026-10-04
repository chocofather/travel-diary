package com.tripbora.service.translation;

public record SharedTranslationCacheBackfillResult(
        long scanned,
        long inserted,
        long skipped
) {
}
