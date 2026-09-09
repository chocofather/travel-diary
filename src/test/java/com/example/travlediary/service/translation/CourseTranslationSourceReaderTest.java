package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.CourseTranslationSource;
import com.example.travlediary.repository.course.CourseMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseTranslationSourceReaderTest {

    @Test
    void readsOnlyCourseTitleAndSanitizedRichTextContent() {
        CourseMapper mapper = mock(CourseMapper.class);
        CourseTranslationSource row = source();
        when(mapper.findVisibleTranslationSource(8L)).thenReturn(row);
        CourseTranslationSourceReader reader =
                new CourseTranslationSourceReader(mapper, new PostContentSanitizer());

        TranslationSourceSnapshot title = reader.findVisible(8L, "title").orElseThrow();
        TranslationSourceSnapshot content = reader.findVisible(8L, "content").orElseThrow();

        assertThat(reader.contentType()).isEqualTo(TranslatableContentType.COURSE);
        assertThat(title.text()).isEqualTo("English course title");
        assertThat(title.mimeType()).isEqualTo("text/plain");
        assertThat(content.text()).isEqualTo("<p>English <strong>course</strong></p>");
        assertThat(content.mimeType()).isEqualTo("text/html");
        assertThat(reader.findVisible(8L, "destinationName")).isEmpty();
    }

    @Test
    void correctsOnlyAnUndeterminedFieldAtTheLockedVersion() {
        CourseMapper mapper = mock(CourseMapper.class);
        CourseTranslationSource row = source();
        when(mapper.findVisibleTranslationSourceForUpdate(8L)).thenReturn(row);
        CourseTranslationSourceReader reader =
                new CourseTranslationSourceReader(mapper, new PostContentSanitizer());

        reader.correctSourceLanguage(
                reader.findVisibleForUpdate(8L, "title").orElseThrow(), "en");
        reader.correctSourceLanguage(
                reader.findVisibleForUpdate(8L, "content").orElseThrow(), "ja");

        verify(mapper).correctTitleSourceLanguage(8L, "en", row.getUpdatedAt());
        verify(mapper).correctContentSourceLanguage(8L, "ja", row.getUpdatedAt());
    }

    @Test
    void deletedModeratedOrMissingCourseReturnsNoSourceBeforeCacheLookup() {
        CourseMapper mapper = mock(CourseMapper.class);
        CourseTranslationSourceReader reader =
                new CourseTranslationSourceReader(mapper, new PostContentSanitizer());

        assertThat(reader.findVisible(99L, "title")).isEmpty();
        assertThat(reader.findVisibleForUpdate(99L, "content")).isEmpty();
    }

    private CourseTranslationSource source() {
        CourseTranslationSource row = new CourseTranslationSource();
        row.setContentId(8L);
        row.setTitle("English course title");
        row.setContent("<p onclick=\"evil()\">English <strong>course</strong><script>bad()</script></p>");
        row.setTitleSourceLanguage("und");
        row.setContentSourceLanguage("und");
        row.setUpdatedAt(Timestamp.from(Instant.parse("2026-09-09T01:00:00Z")));
        return row;
    }
}
