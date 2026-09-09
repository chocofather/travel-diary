package com.example.travlediary.service.translation;

public record PostCommentLanguageBackfillResult(
        long scanned,
        long updated,
        long undetermined
) {
}
