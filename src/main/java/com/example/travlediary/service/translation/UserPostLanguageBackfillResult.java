package com.example.travlediary.service.translation;

public record UserPostLanguageBackfillResult(
        long scanned,
        long titleUpdated,
        long contentUpdated,
        long undeterminedFields
) {
}
