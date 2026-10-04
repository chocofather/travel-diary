package com.tripbora.service.translation;

public record CourseCommentLanguageBackfillResult(
        long scanned,
        long updated,
        long undetermined
) {
}
