package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.CourseLanguageBackfillRow;
import com.example.travlediary.repository.course.CourseMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CourseLanguageBackfillServiceTest {

    @Test
    void fillsOnlyUndeterminedFieldsUsingLocalTextDetectionAndVersionChecks() {
        CourseMapper mapper = mock(CourseMapper.class);
        Timestamp version = Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));
        CourseLanguageBackfillRow row = row(5L, "A memorable Seoul itinerary",
                "<p>この旅行コースは本当に素晴らしい体験でした。</p>", "und", "und", version);
        when(mapper.findUndeterminedCourseLanguagesAfter(0L, 2)).thenReturn(List.of(row));
        when(mapper.findUndeterminedCourseLanguagesAfter(5L, 2)).thenReturn(List.of());
        when(mapper.updateCourseTitleLanguageIfUndetermined(
                5L, "en", row.getTitle(), version)).thenReturn(1);
        when(mapper.updateCourseContentLanguageIfUndetermined(
                5L, "ja", row.getContent(), version)).thenReturn(1);

        CourseLanguageBackfillResult result = new CourseLanguageBackfillService(
                mapper, new LocalContentLanguageDetector(), new PostContentSanitizer(), 2).run();

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.titleUpdated()).isEqualTo(1);
        assertThat(result.contentUpdated()).isEqualTo(1);
        verify(mapper).updateCourseTitleLanguageIfUndetermined(5L, "en", row.getTitle(), version);
        verify(mapper).updateCourseContentLanguageIfUndetermined(5L, "ja", row.getContent(), version);
    }

    @Test
    void imageStructureAndSymbolsRemainUndeterminedWithoutUpdates() {
        CourseMapper mapper = mock(CourseMapper.class);
        CourseLanguageBackfillRow row = row(7L, "123 😊",
                "<p><img src=\"/uploads/a.png\"></p>", "und", "und",
                Timestamp.from(Instant.parse("2026-09-09T01:00:00Z")));
        when(mapper.findUndeterminedCourseLanguagesAfter(0L, 500)).thenReturn(List.of(row));
        when(mapper.findUndeterminedCourseLanguagesAfter(7L, 500)).thenReturn(List.of());

        CourseLanguageBackfillResult result = new CourseLanguageBackfillService(
                mapper, new LocalContentLanguageDetector(), new PostContentSanitizer(), 500).run();

        assertThat(result.undeterminedFields()).isEqualTo(2);
        verify(mapper).findUndeterminedCourseLanguagesAfter(0L, 500);
        verify(mapper).findUndeterminedCourseLanguagesAfter(7L, 500);
        verifyNoMoreInteractions(mapper);
    }

    private CourseLanguageBackfillRow row(Long id, String title, String content,
                                           String titleLanguage, String contentLanguage,
                                           Timestamp updatedAt) {
        CourseLanguageBackfillRow row = new CourseLanguageBackfillRow();
        row.setId(id);
        row.setTitle(title);
        row.setContent(content);
        row.setTitleSourceLanguage(titleLanguage);
        row.setContentSourceLanguage(contentLanguage);
        row.setUpdatedAt(updatedAt);
        return row;
    }
}
