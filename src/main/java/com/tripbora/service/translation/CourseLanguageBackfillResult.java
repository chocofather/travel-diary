package com.tripbora.service.translation;

public record CourseLanguageBackfillResult(
        long scanned,
        long titleUpdated,
        long contentUpdated,
        long undeterminedFields
) {
}
