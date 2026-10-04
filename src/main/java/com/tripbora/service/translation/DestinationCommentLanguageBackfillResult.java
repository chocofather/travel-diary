package com.tripbora.service.translation;

public record DestinationCommentLanguageBackfillResult(
        long scanned,
        long updated,
        long undetermined
) {
}
