package com.tripbora.service.translation;

public record PostCommentLanguageBackfillResult(
        long scanned,
        long updated,
        long undetermined
) {
}
