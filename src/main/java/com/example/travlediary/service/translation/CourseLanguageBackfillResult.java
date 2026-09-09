package com.example.travlediary.service.translation;

public record CourseLanguageBackfillResult(
        long scanned,
        long titleUpdated,
        long contentUpdated,
        long undeterminedFields
) {
}
