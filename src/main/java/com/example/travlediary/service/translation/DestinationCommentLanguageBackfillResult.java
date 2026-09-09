package com.example.travlediary.service.translation;

public record DestinationCommentLanguageBackfillResult(
        long scanned,
        long updated,
        long undetermined
) {
}
