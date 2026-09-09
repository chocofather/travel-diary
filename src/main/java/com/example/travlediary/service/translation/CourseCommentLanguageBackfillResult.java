package com.example.travlediary.service.translation;

public record CourseCommentLanguageBackfillResult(
        long scanned,
        long updated,
        long undetermined
) {
}
