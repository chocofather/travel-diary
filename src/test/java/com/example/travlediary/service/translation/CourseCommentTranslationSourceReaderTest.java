package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.CourseCommentTranslationSource;
import com.example.travlediary.repository.course.CourseCommentMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseCommentTranslationSourceReaderTest {

    @Test
    void readsCourseCommentContentByIdAndCorrectsOnlyTheSameUndeterminedVersion() {
        CourseCommentMapper mapper = mock(CourseCommentMapper.class);
        Timestamp updatedAt = Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));
        CourseCommentTranslationSource row = new CourseCommentTranslationSource();
        row.setContentId(8L);
        row.setSourceText("A sufficiently clear English course comment.");
        row.setSourceLanguage("und");
        row.setUpdatedAt(updatedAt);
        when(mapper.findVisibleTranslationSource(8L)).thenReturn(row);
        CourseCommentTranslationSourceReader reader =
                new CourseCommentTranslationSourceReader(mapper);

        TranslationSourceSnapshot snapshot = reader.findVisible(8L).orElseThrow();
        reader.correctSourceLanguage(snapshot, "en");

        assertThat(reader.contentType()).isEqualTo(TranslatableContentType.COURSE_COMMENT);
        assertThat(snapshot.text()).isEqualTo("A sufficiently clear English course comment.");
        verify(mapper).correctSourceLanguage(8L, "en", updatedAt);
    }

    @Test
    void deletedHiddenOrUnavailableCourseCommentReturnsNoSource() {
        CourseCommentMapper mapper = mock(CourseCommentMapper.class);
        CourseCommentTranslationSourceReader reader =
                new CourseCommentTranslationSourceReader(mapper);

        assertThat(reader.findVisible(99L)).isEmpty();
        assertThat(reader.findVisibleForUpdate(99L)).isEmpty();
    }
}
