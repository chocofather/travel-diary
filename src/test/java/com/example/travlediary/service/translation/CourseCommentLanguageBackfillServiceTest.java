package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.CourseCommentLanguageBackfillRow;
import com.example.travlediary.repository.course.CourseCommentMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseCommentLanguageBackfillServiceTest {

    @Test
    void classifiesOnlyUndeterminedRowsInKeysetBatchesWithoutAProvider() {
        CourseCommentMapper mapper = mock(CourseCommentMapper.class);
        Timestamp version = Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));
        CourseCommentLanguageBackfillRow korean = row(3L, "맛집", version);
        CourseCommentLanguageBackfillRow ambiguous = row(8L, "123 😊", version);
        when(mapper.findUndeterminedLanguagesAfter(0L, 2))
                .thenReturn(List.of(korean, ambiguous));
        when(mapper.findUndeterminedLanguagesAfter(8L, 2)).thenReturn(List.of());
        when(mapper.updateDetectedLanguageIfUndetermined(
                3L, "ko", korean.getContent(), version)).thenReturn(1);

        CourseCommentLanguageBackfillResult result = new CourseCommentLanguageBackfillService(
                mapper, new LocalContentLanguageDetector(), 2).run();

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.undetermined()).isEqualTo(1);
        verify(mapper).updateDetectedLanguageIfUndetermined(
                3L, "ko", korean.getContent(), version);
        verify(mapper).findUndeterminedLanguagesAfter(8L, 2);
    }

    private CourseCommentLanguageBackfillRow row(Long id, String content, Timestamp updatedAt) {
        CourseCommentLanguageBackfillRow row = new CourseCommentLanguageBackfillRow();
        row.setId(id);
        row.setContent(content);
        row.setUpdatedAt(updatedAt);
        return row;
    }
}
