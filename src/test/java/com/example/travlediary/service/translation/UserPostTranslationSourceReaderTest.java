package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.UserPostTranslationSource;
import com.example.travlediary.repository.post.PostMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPostTranslationSourceReaderTest {

    @Test
    void readsTitleAndSanitizedHtmlContentAsIndependentFields() {
        PostMapper mapper = mock(PostMapper.class);
        UserPostTranslationSource row = source();
        when(mapper.findVisibleTranslationSource(8L)).thenReturn(row);
        UserPostTranslationSourceReader reader =
                new UserPostTranslationSourceReader(mapper, new PostContentSanitizer());

        TranslationSourceSnapshot title = reader.findVisible(8L, "title").orElseThrow();
        TranslationSourceSnapshot content = reader.findVisible(8L, "content").orElseThrow();

        assertThat(reader.contentType()).isEqualTo(TranslatableContentType.USER_POST);
        assertThat(title.sourceField()).isEqualTo("title");
        assertThat(title.text()).isEqualTo("English travel title");
        assertThat(title.mimeType()).isEqualTo("text/plain");
        assertThat(content.sourceField()).isEqualTo("content");
        assertThat(content.text()).isEqualTo("<p>English <strong>body</strong></p>");
        assertThat(content.mimeType()).isEqualTo("text/html");
        assertThat(reader.findVisible(8L, "unknown")).isEmpty();
    }

    @Test
    void correctsOnlyTheUndeterminedFieldFromTheSameVersion() {
        PostMapper mapper = mock(PostMapper.class);
        UserPostTranslationSource row = source();
        when(mapper.findVisibleTranslationSourceForUpdate(8L)).thenReturn(row);
        UserPostTranslationSourceReader reader =
                new UserPostTranslationSourceReader(mapper, new PostContentSanitizer());

        TranslationSourceSnapshot title = reader.findVisibleForUpdate(8L, "title").orElseThrow();
        TranslationSourceSnapshot content = reader.findVisibleForUpdate(8L, "content").orElseThrow();
        reader.correctSourceLanguage(title, "en");
        reader.correctSourceLanguage(content, "ja");

        verify(mapper).correctTitleSourceLanguage(8L, "en", row.getUpdatedAt());
        verify(mapper).correctContentSourceLanguage(8L, "ja", row.getUpdatedAt());
    }

    @Test
    void deletedHiddenOrUnavailablePostReturnsNoField() {
        PostMapper mapper = mock(PostMapper.class);
        UserPostTranslationSourceReader reader =
                new UserPostTranslationSourceReader(mapper, new PostContentSanitizer());

        assertThat(reader.findVisible(99L, "title")).isEmpty();
        assertThat(reader.findVisibleForUpdate(99L, "content")).isEmpty();
    }

    private UserPostTranslationSource source() {
        UserPostTranslationSource row = new UserPostTranslationSource();
        row.setContentId(8L);
        row.setTitle("English travel title");
        row.setContent("<p onclick=\"evil()\">English <strong>body</strong><script>bad()</script></p>");
        row.setTitleSourceLanguage("und");
        row.setContentSourceLanguage("und");
        row.setUpdatedAt(Timestamp.from(Instant.parse("2026-09-09T01:00:00Z")));
        return row;
    }
}
